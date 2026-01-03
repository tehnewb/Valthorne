package valthorne.sound;

import valthorne.asset.AssetParameters;

public class SoundParameters implements AssetParameters {

    private final String name;

    public SoundParameters(String name) {
        this.name = name;
    }

    @Override
    public String key() {
        return name;
    }
}
