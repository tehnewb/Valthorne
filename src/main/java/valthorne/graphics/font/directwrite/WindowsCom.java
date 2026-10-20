package valthorne.graphics.font.directwrite;

import static valthorne.graphics.font.directwrite.WindowsTextAbi.*;

import java.lang.foreign.Arena;
import java.lang.foreign.AddressLayout;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;

/**
 * Thread-confined Windows COM ABI access for the DirectWrite backend. The JDK's
 * foreign linker calls installed Windows DLLs directly; no replacement rasterizer,
 * generated native binary, or external Java dependency participates. Only native
 * resource preparation uses dynamic invocation; cached text drawing never enters COM.
 * Requires a 64-bit Windows JVM with the foreign-function API available (JDK 25).
 */
final class WindowsCom implements AutoCloseable {
    /**
     * Pointer argument and result layout used by all supported 64-bit Windows ABIs.
     */
    static final AddressLayout ADDRESS = ValueLayout.ADDRESS;
    /**
     * Signed 32-bit HRESULT, BOOL, and enum argument layout.
     */
    static final ValueLayout.OfInt INT = ValueLayout.JAVA_INT;
    /**
     * Native single-precision coordinate layout.
     */
    static final ValueLayout.OfFloat FLOAT = ValueLayout.JAVA_FLOAT;
    private final Arena arena = Arena.ofConfined(); // Owns DLL lookups and constant native data for this backend.
    private final Linker linker = Linker.nativeLinker(); // Platform ABI implementation supplied by the running JDK.
    private final Map<String, SymbolLookup> libraries = new HashMap<>(); // DLLs loaded once for the backend lifetime.
    private final Map<String, MethodHandle> methods = new HashMap<>(); // Cached COM downcalls keyed by interface slot and signature.
    private final Thread thread = Thread.currentThread(); // Native ownership and apartment thread.
    private final boolean uninitialize; // Whether this instance owns one successful COM apartment initialization.

    /**
     * Enters the current COM apartment without changing an existing apartment mode.
     *
     * @throws IllegalStateException if Windows cannot initialize COM
     */
    WindowsCom() {
        int result = (int) function("ole32", "CoInitializeEx", INT, ADDRESS, INT).invoke(MemorySegment.NULL, 0);
        if (result != 0x80010106) check(result, "CoInitializeEx");
        uninitialize = result >= 0;
    }

    /**
     * Allocates a zero-terminated UTF-16 Windows string in the supplied scope.
     *
     * @param scope temporary or retained native scope
     * @param value Java UTF-16 contents
     * @return owned native wide string
     */
    static MemorySegment wide(Arena scope, String value) {
        /*
         * Windows wchar_t is always a two-byte UTF-16 code unit, independently of
         * the host C linker's wchar_t conventions on other operating systems.
         */
        byte[] bytes = value.getBytes(StandardCharsets.UTF_16LE);
        MemorySegment result = scope.allocate(bytes.length + 2L, 2);
        MemorySegment.copy(bytes, 0, result, ValueLayout.JAVA_BYTE, 0, bytes.length);
        return result;
    }

    /**
     * Stores a Windows GUID with its three little-endian leading fields.
     *
     * @param scope destination lifetime
     * @param value conventional GUID text
     * @return 16-byte GUID
     */
    static MemorySegment guid(Arena scope, String value) {
        /*
         * GUID byte order differs from UUID network order in the first eight bytes.
         */
        UUID uuid = UUID.fromString(value);
        long upper = uuid.getMostSignificantBits();
        long lower = uuid.getLeastSignificantBits();
        MemorySegment result = scope.allocate(16, 4);
        result.set(INT, 0, (int) (upper >>> 32));
        result.set(ValueLayout.JAVA_SHORT, 4, (short) (upper >>> 16));
        result.set(ValueLayout.JAVA_SHORT, 6, (short) upper);
        for (int i = 0; i < 8; i++) result.set(ValueLayout.JAVA_BYTE, 8 + i, (byte) (lower >>> (56 - i * 8)));
        return result;
    }

    /**
     * Rejects failing HRESULTs while retaining the exact native error code.
     *
     * @param result native result
     * @param operation API operation name
     */
    static void check(int result, String operation) {
        /*
         * Success includes S_FALSE; testing equality to S_OK would reject valid COM results.
         */
        if (result < 0) throw new IllegalStateException(operation + " failed: HRESULT 0x" + Integer.toHexString(result));
    }

    /**
     * Returns a checked native function wrapper loaded from an installed Windows DLL.
     *
     * @param library DLL base name
     * @param name exported symbol
     * @param result result layout, or null for void
     * @param arguments argument layouts
     * @return native downcall
     */
    WindowsFunction function(String library, String name, MemoryLayout result, MemoryLayout... arguments) {
        checkThread();
        SymbolLookup lookup = libraries.computeIfAbsent(library, key -> SymbolLookup.libraryLookup(key + ".dll", arena));
        FunctionDescriptor descriptor = result == null ? FunctionDescriptor.ofVoid(arguments) : FunctionDescriptor.of(result, arguments);
        MethodHandle handle = linker.downcallHandle(lookup.find(name).orElseThrow(), descriptor);
        return new WindowsFunction(handle);
    }

    /**
     * Invokes a COM slot. Each interface reference must be live and belong to this thread.
     *
     * @param object native interface pointer
     * @param slot vtable index including IUnknown entries
     * @param result result layout, or null for void
     * @param layouts argument layouts excluding the implicit interface pointer
     * @param arguments arguments excluding the implicit interface pointer
     * @return boxed native result, or null for void
     */
    Object call(MemorySegment object, int slot, MemoryLayout result, MemoryLayout[] layouts, Object... arguments) {
        checkThread();
        if (object.address() == 0) throw new IllegalStateException("Null COM interface");
        MemorySegment table = object.reinterpret(8).get(ADDRESS, 0).reinterpret((slot + 1L) * 8);
        MemorySegment address = table.get(ADDRESS, slot * 8L);
        String key = address.address() + ":" + result + ":" + Arrays.toString(layouts);
        MethodHandle handle = methods.get(key);
        if (handle == null) {
            MemoryLayout[] signature = new MemoryLayout[layouts.length + 1];
            signature[0] = ADDRESS;
            System.arraycopy(layouts, 0, signature, 1, layouts.length);
            handle = linker.downcallHandle(address, result == null ? FunctionDescriptor.ofVoid(signature) : FunctionDescriptor.of(result, signature));
            methods.put(key, handle);
        }
        Object[] values = new Object[arguments.length + 1];
        values[0] = object;
        System.arraycopy(arguments, 0, values, 1, arguments.length);
        return new WindowsFunction(handle).invoke(values);
    }

    /**
     * Calls an HRESULT-returning COM method and checks its status.
     *
     * @param object live COM interface
     * @param slot vtable slot
     * @param layouts explicit argument layouts
     * @param arguments explicit argument values
     */
    void hr(MemorySegment object, int slot, MemoryLayout[] layouts, Object... arguments) {
        check((int) call(object, slot, INT, layouts, arguments), "COM slot " + slot);
    }

    /**
     * Releases an owned interface reference, accepting null for partial-construction cleanup.
     *
     * @param object owned COM reference, or NULL
     */
    void release(MemorySegment object) {
        if (object.address() != 0) call(object, UNKNOWN_RELEASE, INT, new MemoryLayout[0]);
    }

    /**
     * Ensures native references are never used from a different apartment/thread.
     */
    private void checkThread() {
        if (Thread.currentThread() != thread) throw new IllegalStateException("DirectWrite resources are thread confined");
    }

    @Override
    public void close() {
        checkThread();
        if (uninitialize) function("ole32", "CoUninitialize", null).invoke();
        arena.close();
    }
}
