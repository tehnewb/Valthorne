package valthorne.graphics.model;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.junit.jupiter.api.Test;
import valthorne.camera.Camera3D;
import valthorne.graphics.Color;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Compares renderer collection with independent snapshots before either is reused.
 * Fixtures contain CPU geometry only, so these contracts require no GL context.
 *
 * @author Albert Beaupre
 */
class PathTracingSceneSnapshotTest {
    @Test
    void directMutableValuesAndTransformsMatchFreshSnapshots() {
        var collector = new PathTracingScene.Collector();
        var scene = new Scene3D();
        var material = material();
        var instance = new ModelInstance3D().setModel(ModelBuilder3D.box(1, 2, 3))
                .setMaterial(material).setPosition(2, 3, 4).setScale(-1, 2, .5f);
        scene.add(instance);
        long previous = equivalent(scene, collector).signature();
        List<Runnable> edits = List.of(
                () -> instance.getPosition().add(.5f, -1, 2),
                () -> instance.getScale().set(2, -.75f, 1.25f),
                () -> instance.setRotation(.2f, -.4f, .7f),
                () -> instance.setRotation(new Quaternionf().rotationXYZ(-.3f, .2f, .8f)),
                () -> instance.setParentTransform(new Matrix4f().translate(-2, 4, 1)
                        .rotateZ(.4f).scale(1, 2, 3)),
                () -> material.getTint().set(.3f, .6f, .9f, .4f),
                () -> material.getEmissive().set(.9f, .2f, .4f, .5f),
                () -> material.setRoughness(.81f),
                () -> material.setMetallic(.76f),
                () -> material.setTransmission(.37f),
                () -> material.setIndexOfRefraction(1.81f),
                () -> material.setEmissionStrength(4.5f),
                () -> material.setEmissionLightEnabled(!material.isEmissionLightEnabled()),
                () -> material.setRenderPass(RenderPass3D.ADDITIVE),
                () -> material.setCastsShadow(!material.isCastsShadow()),
                () -> material.setReceivesShadow(!material.isReceivesShadow()),
                () -> material.setAlphaCutoff(.31f),
                () -> instance.setModel(ModelBuilder3D.plane(3, 2)));
        for (int i = 0; i < edits.size(); i++) {
            edits.get(i).run();
            long current = equivalent(scene, collector).signature();
            assertNotEquals(previous, current, "Mutable signature field edit " + i);
            previous = current;
        }
        // These raster settings are borrowed even though they are not signature fields.
        material.setLightingMix(.13f).setFogMix(.24f).setRadianceMix(.35f)
                .setDepthTest(true).setDepthWrite(true).setCullBackFaces(false);
        assertEquals(previous, equivalent(scene, collector).signature());
        collector.clear();
    }

    @Test
    void hierarchyVisibilityOrderingAndReparentingMatchFreshSnapshots() {
        var collector = new PathTracingScene.Collector();
        var scene = new Scene3D();
        var looseModel = ModelBuilder3D.plane(1, 1);
        var childModel = ModelBuilder3D.box(1, 1, 1);
        var grandchildModel = ModelBuilder3D.plane(2, 1);
        var loose = new ModelInstance3D().setModel(looseModel).setPosition(8, 0, 0);
        scene.add(loose);
        scene.add(loose); // Loose duplicates intentionally retain submission order.
        var root = new SceneNode3D().setPosition(2, 3, 4).setScale(2, 1, 3)
                .setRotation(.2f, .4f, -.3f);
        var child = new SceneNode3D().setModel(childModel).setPosition(1, -2, .5f)
                .setRotation(.7f, -.2f, .1f).setScale(-1, .5f, 2);
        var grandchild = new SceneNode3D().setModel(grandchildModel).setPosition(0, 1, 2);
        root.addChild(child);
        child.addChild(grandchild);
        scene.addNode(root);
        assertModels(equivalent(scene, collector), looseModel, looseModel, childModel, grandchildModel);
        root.getPosition().add(-1, 2, 3);
        child.getScale().set(.3f, -2, 1.1f);
        grandchild.setRotation(new Quaternionf().rotationXYZ(.3f, .7f, -.5f));
        equivalent(scene, collector);
        root.setVisible(false);
        assertModels(equivalent(scene, collector), looseModel, looseModel);
        root.setVisible(true);
        child.setVisible(false);
        assertModels(equivalent(scene, collector), looseModel, looseModel);
        root.addChild(grandchild); // A hidden old parent no longer hides the moved child.
        assertModels(equivalent(scene, collector), looseModel, looseModel, grandchildModel);
        child.setVisible(true);
        assertModels(equivalent(scene, collector), looseModel, looseModel, childModel, grandchildModel);
        loose.setVisible(false);
        assertModels(equivalent(scene, collector), childModel, grandchildModel);
        scene.removeNode(root);
        assertModels(equivalent(scene, collector));
        collector.clear();
    }

    @Test
    void shrinkingEmptyGeometryAndRegrowingDoNotReuseStaleInstancesOrBuildData() {
        var collector = new PathTracingScene.Collector();
        var scene = new Scene3D();
        var mesh = ModelBuilder3D.plane(1, 1);
        var empty = new Model3D(new Model3D.Triangle[0]);
        for (int count : new int[]{0, 1, 16, 3, 0, 32, 2, 0, 1}) {
            scene.clear();
            scene.add(new ModelInstance3D()); // Null models do not become instances.
            scene.add(new ModelInstance3D().setModel(empty)); // Empty models still enter the signature.
            for (int i = 0; i < count; i++) {
                scene.add(new ModelInstance3D().setModel(mesh).setPosition(i, i % 3, .25f * i)
                        .setMaterial(new Material3D().setEmissive(1, .3f, .1f, i % 2)
                                .setEmissionStrength(i + 1)));
            }
            var snapshot = equivalent(scene, collector);
            assertEquals(count + 1, snapshot.instances.size());
            assertSame(empty, snapshot.instances.get(0).model());
            assertEquals(count * 2, snapshot.triangles.size());
        }
        scene.clear();
        var emptySnapshot = equivalent(scene, collector);
        assertModels(emptySnapshot);
        assertArrayEquals(new float[48], emptySnapshot.triangleData());
        assertArrayEquals(new float[8], emptySnapshot.nodeData());
        assertArrayEquals(new float[2], emptySnapshot.emitterData());
        collector.clear();
        scene.add(new ModelInstance3D().setModel(mesh));
        assertModels(equivalent(scene, collector), mesh);
        collector.clear();
    }

    @Test
    void consumerCompactionDoesNotChangeTheNextCollectedOrderOrTransforms() {
        var collector = new PathTracingScene.Collector();
        var scene = new Scene3D();
        var first = new ModelInstance3D().setModel(ModelBuilder3D.plane(1, 2));
        var last = new ModelInstance3D().setModel(ModelBuilder3D.box(2, 1, 3));
        var empty = new ModelInstance3D().setModel(new Model3D(new Model3D.Triangle[0]));
        scene.add(empty);
        scene.add(first);
        scene.add(empty);
        scene.add(last);
        for (int frame = 0; frame < 6; frame++) {
            first.setPosition(frame, 2, 3).setScale(1, frame + 1, 2);
            last.setPosition(-frame, -1, .5f).setRotation(.1f * frame, .3f, -.2f);
            var snapshot = equivalent(scene, collector);
            // Filament removes empty geometry after computing the complete signature.
            snapshot.instances.removeIf(instance -> instance.model().triangles().length == 0);
            assertModels(snapshot, first.getModel(), last.getModel());
        }
        collector.clear();
    }

    @Test
    void objExpansionPreservesColorsPassesAndBorrowedSourceMaterialsAcrossSlotChanges() {
        var collector = new PathTracingScene.Collector();
        var scene = new Scene3D();
        var direct = material();
        var directBefore = direct.copy();
        var mesh = ModelBuilder3D.plane(1, 1);
        var instance = new ModelInstance3D().setModel(mesh).setMaterial(direct);
        scene.add(instance);
        equivalent(scene, collector);
        var obj = twoPartObj();
        try {
            var imported = material().setRenderPass(RenderPass3D.OPAQUE).setDepthWrite(false)
                    .setTransmission(0);
            var sourceBefore = imported.copy();
            var firstPartBefore = obj.getParts().get(0).material().copy();
            var secondPartBefore = obj.getParts().get(1).material().copy();
            instance.setModel(obj).setMaterial(imported).setPosition(2, -1, 3);
            var snapshot = equivalent(scene, collector);
            assertModels(snapshot, obj.getParts().get(0).model(), obj.getParts().get(1).model());
            assertEquals(RenderPass3D.OPAQUE, snapshot.instances.get(0).material().getRenderPass());
            assertEquals(RenderPass3D.TRANSLUCENT, snapshot.instances.get(1).material().getRenderPass());
            assertFalse(snapshot.instances.get(0).material().isDepthWrite());
            assertMaterial(sourceBefore, imported);
            assertMaterial(firstPartBefore, obj.getParts().get(0).material());
            assertMaterial(secondPartBefore, obj.getParts().get(1).material());
            assertMaterial(directBefore, direct);

            // Recopy all inherited values into reused part slots, including live colors.
            imported.setLightingMix(.15f).setFogMix(.26f).setRadianceMix(.37f)
                    .setRoughness(.83f).setMetallic(.74f).setEmissionStrength(5)
                    .setEmissionLightEnabled(true).setCastsShadow(true).setReceivesShadow(true)
                    .setDepthTest(true).setCullBackFaces(false).setAlphaCutoff(.25f);
            imported.getTint().set(.31f, .57f, .89f, .42f);
            imported.getEmissive().set(.17f, .72f, .23f, .61f);
            obj.getParts().get(0).material().getTint().set(.27f, .64f, .35f, .53f);
            equivalent(scene, collector);
            imported.setTransmission(.6f).setIndexOfRefraction(1.9f);
            snapshot = equivalent(scene, collector);
            assertEquals(RenderPass3D.OPAQUE, snapshot.instances.get(1).material().getRenderPass(),
                    "Transmission preserves the explicit parent pass");
            imported.setRenderPass(RenderPass3D.ADDITIVE).setDepthWrite(true);
            snapshot = equivalent(scene, collector);
            assertEquals(RenderPass3D.ADDITIVE, snapshot.instances.get(1).material().getRenderPass());
            assertTrue(snapshot.instances.get(1).material().isDepthWrite());

            instance.setModel(mesh).setMaterial(direct);
            snapshot = equivalent(scene, collector);
            assertModels(snapshot, mesh);
            assertSame(direct, snapshot.instances.get(0).material());
            assertMaterial(directBefore, direct);
            instance.setModel(obj).setMaterial(new Material3D());
            equivalent(scene, collector);
            scene.clear();
            assertModels(equivalent(scene, collector));
            collector.clear();
            scene.add(instance);
            equivalent(scene, collector);
        } finally {
            collector.clear();
            obj.dispose();
        }
    }

    @Test
    void hiddenUnsupportedRenderablesAreSkippedAndFailureDoesNotPoisonLaterCaptures() {
        var collector = new PathTracingScene.Collector();
        var scene = new Scene3D();
        var mesh = ModelBuilder3D.plane(1, 1);
        var instance = new ModelInstance3D().setModel(mesh);
        var unsupported = new UnsupportedRenderable();
        scene.add(instance);
        scene.add(unsupported);
        unsupported.visible = false;
        assertModels(equivalent(scene, collector), mesh);
        unsupported.visible = true;
        var expected = assertThrows(IllegalArgumentException.class, () -> new PathTracingScene(scene));
        var actual = assertThrows(IllegalArgumentException.class, () -> collector.capture(scene));
        assertEquals(expected.getMessage(), actual.getMessage());
        scene.remove(unsupported);
        instance.getPosition().set(2, -1, 3);
        assertModels(equivalent(scene, collector), mesh);
        instance.getPosition().x = Float.NaN;
        assertThrows(IllegalArgumentException.class, () -> collector.capture(scene));
        instance.getPosition().x = 1;
        equivalent(scene, collector);
        collector.clear();
    }

    @Test
    void independentConstructorSnapshotsRetainCapturedTransformsAcrossCollectorUse() {
        var scene = new Scene3D();
        var mesh = ModelBuilder3D.box(1, 2, 3);
        var instance = new ModelInstance3D().setModel(mesh).setPosition(2, 3, 4);
        scene.add(instance);
        var first = new PathTracingScene(scene);
        var sameMoment = new PathTracingScene(scene);
        var frozenTransform = new Matrix4f(first.instances.get(0).transform());
        long frozenSignature = first.signature();
        var collector = new PathTracingScene.Collector();
        equivalent(scene, collector);
        instance.setPosition(8, 9, 10).setScale(-2, 1, 3);
        equivalent(scene, collector);
        collector.clear();
        assertEquals(frozenSignature, first.signature());
        assertEquals(frozenTransform, first.instances.get(0).transform());
        first.build();
        sameMoment.build();
        assertPacked(sameMoment, first);
        assertNotEquals(instance.getWorldTransform(new Matrix4f()), first.instances.get(0).transform());
    }

    @Test
    void aPartiallyBuiltSingularSnapshotDoesNotContaminateTheNextCapture() {
        var collector = new PathTracingScene.Collector();
        var scene = new Scene3D();
        scene.add(new ModelInstance3D().setModel(ModelBuilder3D.plane(2, 3)));
        var node = new SceneNode3D().setModel(ModelBuilder3D.box(1, 1, 1));
        scene.addNode(node);
        equivalent(scene, collector);
        node.getScale().z = 0;
        var actual = collector.capture(scene);
        var expected = new PathTracingScene(scene);
        assertThrows(IllegalStateException.class, expected::build);
        assertThrows(IllegalStateException.class, actual::build);
        node.getScale().z = 2;
        equivalent(scene, collector);
        collector.clear();
    }

    /** Builds both snapshots once and compares all observable collection/build data. */
    private static PathTracingScene equivalent(Scene3D source, PathTracingScene.Collector collector) {
        PathTracingScene actual = collector.capture(source);
        PathTracingScene expected = new PathTracingScene(source);
        assertEquals(expected.signature(), actual.signature());
        assertEquals(expected.instances.size(), actual.instances.size());
        for (int i = 0; i < expected.instances.size(); i++) {
            var a = expected.instances.get(i);
            var b = actual.instances.get(i);
            assertSame(a.model(), b.model(), "Model order at instance " + i);
            assertArrayEquals(a.transform().get(new float[16]), b.transform().get(new float[16]),
                    "Captured world transform at instance " + i);
            assertMaterial(a.material(), b.material());
        }
        expected.build();
        actual.build();
        assertPacked(expected, actual);
        return actual;
    }

    private static void assertPacked(PathTracingScene expected, PathTracingScene actual) {
        assertEquals(expected.textures, actual.textures);
        assertEquals(expected.triangles.size(), actual.triangles.size());
        assertEquals(expected.nodes.size(), actual.nodes.size());
        assertEquals(expected.emitters, actual.emitters);
        assertArrayEquals(expected.triangleData(), actual.triangleData(), "Packed triangle data");
        assertArrayEquals(expected.nodeData(), actual.nodeData(), "Packed BVH data");
        assertArrayEquals(expected.emitterData(), actual.emitterData(), "Packed emitter data");
    }

    private static void assertModels(PathTracingScene snapshot, Model3D... expected) {
        assertEquals(expected.length, snapshot.instances.size());
        for (int i = 0; i < expected.length; i++) assertSame(expected[i], snapshot.instances.get(i).model());
    }

    private static void assertMaterial(Material3D expected, Material3D actual) {
        assertArrayEquals(values(expected), values(actual), "All material scalar/color values");
        assertSame(expected.getTexture(), actual.getTexture());
        assertEquals(expected.getRenderPass(), actual.getRenderPass());
        assertEquals(expected.isDepthTest(), actual.isDepthTest());
        assertEquals(expected.isDepthWrite(), actual.isDepthWrite());
        assertEquals(expected.isCullBackFaces(), actual.isCullBackFaces());
        assertEquals(expected.isCastsShadow(), actual.isCastsShadow());
        assertEquals(expected.isReceivesShadow(), actual.isReceivesShadow());
        assertEquals(expected.isEmissionLightEnabled(), actual.isEmissionLightEnabled());
    }

    private static float[] values(Material3D m) {
        var tint = m.getTint();
        var emission = m.getEmissive();
        return new float[]{tint.r(), tint.g(), tint.b(), tint.a(), emission.r(), emission.g(),
                emission.b(), emission.a(), m.getLightingMix(), m.getFogMix(), m.getRadianceMix(),
                m.getRoughness(), m.getMetallic(), m.getTransmission(), m.getIndexOfRefraction(),
                m.getEmissionStrength(), m.getAlphaCutoff()};
    }

    private static Material3D material() {
        return new Material3D().setTint(new Color(.8f, .6f, .4f, .7f))
                .setEmissive(.6f, .3f, .1f, .8f).setEmissionStrength(2)
                .setLightingMix(.2f).setFogMix(.3f).setRadianceMix(.4f)
                .setRoughness(.35f).setMetallic(.2f).setTransmission(0).setIndexOfRefraction(1.5f)
                .setEmissionLightEnabled(false).setCastsShadow(false).setReceivesShadow(false)
                .setDepthTest(false).setDepthWrite(false).setCullBackFaces(true).setAlphaCutoff(.07f);
    }

    private static ObjModel3D twoPartObj() {
        String geometry = """
                mtllib parts.mtl
                v 0 0 0
                v 1 0 0
                v 0 1 0
                v 1 1 1
                usemtl copper
                f 1 2 3
                usemtl glass
                f 2 4 3
                """;
        String materials = """
                newmtl copper
                Kd 0.9 0.4 0.2
                d 1
                newmtl glass
                Kd 0.2 0.4 0.9
                d 0.5
                """;
        return ObjModel3D.load("snapshot/parts.obj", path -> {
            if (path.endsWith(".obj")) return geometry.getBytes(StandardCharsets.UTF_8);
            if (path.endsWith(".mtl")) return materials.getBytes(StandardCharsets.UTF_8);
            throw new java.io.IOException("Unexpected fixture resource: " + path);
        }, false);
    }

    private static final class UnsupportedRenderable implements Renderable3D {
        boolean visible;

        @Override public Material3D getMaterial() {return null;}
        @Override public boolean isVisible(Camera3D camera) {return true;}
        @Override public boolean isRenderableVisible() {return visible;}
    }
}
