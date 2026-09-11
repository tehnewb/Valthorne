package valthorne.engine.benchmark;

import java.util.List;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.*;
import valthorne.graphics.particle.Particle3D;
import valthorne.graphics.particle.ParticleEmitter3D;

/** CPU-only pooled particle workloads. Timing/allocation are per complete emitter update. */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(value = 2, jvmArgsAppend = {"-Xms256m", "-Xmx256m"})
@State(Scope.Thread)
public class ParticleEmitter3DBenchmark {
    @Param({"256", "4096"}) public int count;
    private static final float DT = 1f / 64;
    private ParticleEmitter3D steady, expiry, churn;
    private List<Particle3D> steadyParticles, churnParticles;
    private int birth;

    @Setup public void setup() {
        steady = new ParticleEmitter3D(count, p -> {
            p.setLifetime(Float.MAX_VALUE);
            p.getVelocity().set(.3f, -.2f, .4f);
            p.getAcceleration().set(.0001f, .0002f, -.0003f);
        });
        expiry = new ParticleEmitter3D(count, p -> p.setLifetime(DT));
        churn = new ParticleEmitter3D(count, p -> {
            p.setLifetime((++birth & 1) == 0 ? DT : 2 * DT);
            p.getVelocity().set(.3f, -.2f, .4f);
        });
        steady.burst(count);
        expiry.burst(count);
        expiry.update(DT); // Warm the pool before measuring reuse.
        churn.burst(count);
        for (int i = 0; i < 64; i++) {churn.update(DT); churn.burst(count);}
        steadyParticles = steady.getParticles();
        churnParticles = churn.getParticles();
    }

    @Benchmark public float steadyUpdate() {
        steady.update(DT);
        return steadyParticles.getFirst().getPosition().x + steadyParticles.getLast().getPosition().z;
    }

    @Benchmark public int burstAndExpire() {
        int spawned = expiry.burst(count);
        expiry.update(DT);
        return spawned;
    }

    @Benchmark public float interleavedChurn() {
        churn.update(DT);
        int spawned = churn.burst(count);
        return spawned + churnParticles.getFirst().getPosition().x;
    }
}
