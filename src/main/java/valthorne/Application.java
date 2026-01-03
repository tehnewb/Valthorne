package valthorne;

/**
 * The Application interface defines the core lifecycle methods that must be
 * implemented by any OpenGL application using the JGL framework.
 */
public interface Application {

    /**
     * Initializes the application. This method is called once when the application
     * starts and should contain initialization logic such as creating resources,
     * loading assets, and setting up the initial application state.
     */
    void init();

    /**
     * Renders the application's graphics. This method is called once per frame
     * and should contain all rendering logic for displaying the application's
     * visual content.
     */
    void render();

    /**
     * Updates the application's state. This method is called once per frame
     * before rendering, with the time elapsed since the last update.
     *
     * @param delta The time elapsed since the last update in seconds
     */
    void update(float delta);

    /**
     * Cleans up and releases resources used by the application. This method is
     * called once when the application is shutting down and should properly
     * dispose of any resources that were allocated during the application's lifecycle.
     */
    void dispose();

}