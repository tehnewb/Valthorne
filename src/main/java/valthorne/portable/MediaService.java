package valthorne.portable;

/**
 * Owned browser-portable image and sampled-sound assets. Callbacks run on the application thread.
 */
public interface MediaService {
    interface Listener<T> {
        void loaded(T asset);

        void failed(String reason);
    }

    interface Image extends AutoCloseable {
        int width();

        int height();

        /**
         * Draws a full image to the current 2D overlay, in CSS pixels.
         */
        void draw(float x, float y, float width, float height, float alpha);

        @Override
        void close();
    }

    interface Sound extends AutoCloseable {
        /**
         * Returns false if audio is locked or the voice budget is exhausted.
         */
        boolean play(float volume, float pan);

        @Override
        void close();
    }

    AssetService.Request loadImage(String uri, Listener<Image> listener);

    AssetService.Request loadSound(String uri, Listener<Sound> listener);
}
