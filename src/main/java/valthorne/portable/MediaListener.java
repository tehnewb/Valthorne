package valthorne.portable;

/**
 * Application-thread terminal callback for a media request.
 * Successful delivery transfers resource ownership to the recipient.
 *
 * @param <T> owned media type
 */
public interface MediaListener<T> {
    /**
     * Accepts ownership of the loaded media.
     *
     * @param asset owned media that the recipient must eventually close
     */
    void loaded(T asset);

    /**
     * Receives a terminal loading or cancellation failure.
     *
     * @param reason explanation of the failure
     */
    void failed(String reason);
}
