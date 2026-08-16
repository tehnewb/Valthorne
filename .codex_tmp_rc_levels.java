import valthorne.graphics.radiance.*;
public class TmpRadianceInfo {
  public static void main(String[] args) {
    RadianceCascadeSettings s = new RadianceCascadeSettings()
      .setBaseProbeSpacing(1)
      .setBaseRayCount(4)
      .setBilinearFix(true)
      .setTransmittanceCutoff(0.001f)
      .setIntensity(1.0f)
      .setCrossBlur(true)
      .setOpacitySimilarityThreshold(0.02f)
      .setInternalScale(2)
      .setMaxCascadeTextureWidth(8192);
    int width = 1280, height = 720;
    int solveW = Math.max(1, (width + s.getInternalScale() - 1) / s.getInternalScale());
    int solveH = Math.max(1, (height + s.getInternalScale() - 1) / s.getInternalScale());
    float baseIntervalLength = s.getBaseIntervalLength() > 0f ? s.getBaseIntervalLength() : s.getBaseProbeSpacing() * 0.70710677f;
    for (int n = 0; ; n++) {
      int spacing = s.getBaseProbeSpacing() << n;
      int raySide = 2 << n;
      int rayCount = raySide * raySide;
      int probeCountX = Math.max(1, (int)Math.ceil(solveW / (double)spacing));
      int probeCountY = Math.max(1, (int)Math.ceil(solveH / (double)spacing));
      long texW = (long)probeCountX * raySide;
      long texH = (long)probeCountY * raySide;
      if (texW > s.getMaxCascadeTextureWidth() || texH > s.getMaxCascadeTextureWidth()) break;
      float intervalStart = n <= 0 ? 0f : baseIntervalLength * ((float)((Math.pow(4.0, n) - 1.0) / 3.0));
      float intervalLength = baseIntervalLength * (float)Math.pow(4.0, n);
      float paddedWidth = solveW + spacing;
      float paddedHeight = solveH + spacing;
      float maxSceneDistance = (float)Math.hypot(paddedWidth, paddedHeight);
      System.out.printf("level=%d spacing=%d rayCount=%d probes=%dx%d tex=%dx%d interval=[%.2f, %.2f] maxDist=%.2f%n",
          n, spacing, rayCount, probeCountX, probeCountY, texW, texH, intervalStart, intervalStart + intervalLength, maxSceneDistance);
      if (s.getMaxLevels() > 0 && n + 1 >= s.getMaxLevels()) break;
      if (intervalStart + intervalLength >= maxSceneDistance) break;
    }
  }
}
