package valthorne.graphics.texture;

import valthorne.asset.AssetParameters;

public class TextureParameters implements AssetParameters {

    private final String name;

    public TextureParameters(String path) {
        this.name = path;
    }

    @Override
    public String key() {
        return name;
    }
}
