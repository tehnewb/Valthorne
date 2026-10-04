package valthorne.math.physics;

import com.github.stephengold.joltjni.BatchBodyInterface;
import com.github.stephengold.joltjni.BodyIdArray;
import org.lwjgl.system.MemoryUtil;

import java.nio.DoubleBuffer;
import java.nio.FloatBuffer;
import java.util.Arrays;

/**
 * Lazily owned batch pose storage for large moving-body populations. Two JNI
 * calls read every moving pose instead of one call per body. Storage uses 44
 * native bytes plus one Java reference per moving body and is allocated only
 * after the batching threshold is reached. Membership changes rebuild the
 * snapshot before its next use; unchanged steps allocate no objects. Static
 * bodies are excluded. Clearing the world promptly releases all batch storage.
 * All access is confined to the world's owner thread, after native workers join.
 */
final class PhysicsPoses2D implements AutoCloseable {
    /**
     * Small worlds avoid native buffers and use their shared scalar scratch.
     */
    static final int MIN_BODIES = 128;

    private final BatchBodyInterface bodies; // Borrowed native batch interface owned by the world.
    private RigidBody2D[] handles; // Dense moving-body snapshot; null before allocation or after disposal.
    private BodyIdArray ids; // Owned native identifiers in the same order as the snapshot.
    private DoubleBuffer positions; // Owned direct XYZ doubles, freed explicitly rather than by a cleaner.
    private FloatBuffer rotations; // Owned direct XYZW floats, freed explicitly rather than by a cleaner.
    private boolean dirty = true; // Whether native membership changed since the snapshot was assembled.

    /**
     * Borrows the native body interface without allocating batch storage yet.
     *
     * @param bodies live no-lock native batch interface
     */
    PhysicsPoses2D(BatchBodyInterface bodies) {
        this.bodies = bodies;
    }

    /**
     * Invalidates native IDs after creation or destruction without rebuilding
     * repeatedly during bulk scene mutation.
     */
    void invalidate() {
        if (!dirty) Arrays.fill(handles, null);
        dirty = true;
    }

    /**
     * Rebuilds the exact-sized moving snapshot and its explicitly owned buffers.
     * Partial allocation is rolled back before propagating failure.
     *
     * @param live dense live world handles
     * @param count occupied live prefix length
     * @param movingCount number of moving handles in that prefix
     */
    private void rebuild(RigidBody2D[] live, int count, int movingCount) {
        close();
        try {
            handles = new RigidBody2D[movingCount];
            ids = new BodyIdArray(movingCount);
            positions = MemoryUtil.memAllocDouble(movingCount * 3);
            rotations = MemoryUtil.memAllocFloat(movingCount * 4);
            int index = 0;
            for (int i = 0; i < count; i++) {
                RigidBody2D body = live[i];
                if (body.getMotionType() == MotionType2D.STATIC) continue;
                handles[index] = body;
                ids.set(index++, body.id);
            }
            dirty = false;
        } catch (RuntimeException | Error failure) {
            try {
                close();
            } catch (RuntimeException | Error cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
            }
            throw failure;
        }
    }

    /**
     * Copies native poses and advances interpolation endpoints without temporary
     * vectors or per-body JNI calls.
     *
     * @param live dense live world handles
     * @param count occupied live prefix length
     * @param movingCount current number of moving bodies
     */
    void capture(RigidBody2D[] live, int count, int movingCount) {
        if (dirty) rebuild(live, count, movingCount);
        bodies.getPositions(ids, positions);
        bodies.getRotations(ids, rotations);
        for (int i = 0; i < handles.length; i++)
            handles[i].captureStep(positions.get(i * 3), positions.get(i * 3 + 1), rotations.get(i * 4 + 2), rotations.get(i * 4 + 3));
    }

    @Override
    public void close() {
        BodyIdArray ownedIds = ids;
        ids = null;
        try {
            if (ownedIds != null) ownedIds.close();
        } finally {
            MemoryUtil.memFree(positions);
            positions = null;
            MemoryUtil.memFree(rotations);
            rotations = null;
            handles = null;
            dirty = true;
        }
    }
}
