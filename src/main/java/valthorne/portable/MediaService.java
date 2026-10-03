package valthorne.portable;

/**
 * Asynchronous image and sampled-sound loading. Terminal callbacks run on the
 * application thread and transfer ownership of successfully decoded resources.
 */
public interface MediaService {
    /**
     * Requests an owned image resource.
     *
     * @param uri image location supported by the platform
     * @param listener completion callback receiving image ownership
     * @return cancellation action
     */
    AssetRequest loadImage(String uri, MediaListener<MediaImage> listener);

    /**
     * Requests an owned sampled audio resource.
     *
     * @param uri audio location supported by the platform
     * @param listener completion callback receiving sound ownership
     * @return cancellation action
     */
    AssetRequest loadSound(String uri, MediaListener<MediaSound> listener);
}
