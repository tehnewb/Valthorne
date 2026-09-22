package compatibility;

import valthorne.Application;
import valthorne.JGL;
import valthorne.JGLConfiguration;
import valthorne.Window;
import valthorne.graphics.Color;
import valthorne.graphics.shader.ComputeShader;
import valthorne.graphics.texture.FrameBuffer;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public final class CommonComputeApplication implements Application {
    private ComputeShader shader;
    private FrameBuffer target;
    private int storage, frames;

    static void main(String[] args) {
        try {
            JGL.init(new CommonComputeApplication(), JGLConfiguration.defaults().title("Shared compute").contextVersion(4, 3).size(640, 480).visible(false));
            System.out.println("COMMON_COMPUTE_RETURNED");
        } catch (Throwable error) {
            error.printStackTrace();
            throw error;
        }
    }

    public void init() {
        if (!ComputeShader.isComputeSupported()) throw new AssertionError("Compute unavailable");
        target = new FrameBuffer(32, 32, false);
        shader = new ComputeShader("""
                #version 430 core
                layout(local_size_x=8,local_size_y=8) in;
                layout(std430,binding=0) readonly buffer Values { float scale[]; };
                layout(rgba8,binding=0) uniform writeonly image2D destination;
                uniform vec4 color;
                shared float factors[64];
                void main(){uint i=gl_LocalInvocationIndex;factors[i]=scale[0];barrier();imageStore(destination,ivec2(gl_GlobalInvocationID.xy),vec4(color.rgb*factors[63u-i],color.a));}
                """);
        storage = ComputeShader.createSSBO(4, 0x88E8);
        ByteBuffer data = ByteBuffer.allocateDirect(4).order(ByteOrder.nativeOrder());
        data.putFloat(.5f).flip();
        ComputeShader.updateSSBO(storage, 0, data);
        ComputeShader.bindSSBO(storage, 0);
        shader.bind();
        shader.setUniform4f("color", 1, .5f, .25f, 1);
        ComputeShader.bindImage2D(target.getColorTextureId(), 0, 0x88B9, 0x8058);
        shader.dispatch(4, 4, 1);
        ComputeShader.memoryBarrierAll();
        shader.unbind();
        System.out.println("COMMON_COMPUTE_READY");
    }

    public void update(float dt) {
        if (++frames >= 60) Window.requestClose();
    }

    public void render() {
        Window.clear(Color.BLACK);
        target.draw(0, 0, Window.getWidth(), Window.getHeight());
    }

    public void dispose() {
        if (shader != null) {
            shader.dispose();
            shader.dispose();
        }
        if (storage != 0) ComputeShader.deleteSSBO(storage);
        if (target != null) target.dispose();
        System.out.println("COMMON_COMPUTE_VALIDATED");
    }
}
