package ph.edu.dlsu.anilink.util;

import javafx.event.Event;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

/**
 * Utility component for handling JavaFX scene navigation and Spring dependency injection.
 *
 * <p>Window size is preserved across scene transitions so that user resizing is
 * not lost when switching between views.</p>
 */
@Component
public class ViewNavigator {

    private final ApplicationContext applicationContext;

    /**
     * Last known window width, used to preserve size across navigations.
     */
    private double lastWidth;

    /**
     * Last known window height, used to preserve size across navigations.
     */
    private double lastHeight;

    /**
     * Constructs the ViewNavigator with Spring application context.
     *
     * @param applicationContext the Spring context used for controller factory injection
     */
    public ViewNavigator(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    /**
     * Navigates to a new view without returning its controller instance.
     *
     * <p>The current window size is preserved so the UI does not reset to a
     * default size when switching views.</p>
     *
     * @param event the event triggering the navigation
     * @param fxmlPath the resource path to the target FXML file
     * @param width the preferred window width (used as fallback on first navigation)
     * @param height the preferred window height (used as fallback on first navigation)
     */
    public void navigateTo(Event event, String fxmlPath, double width, double height) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(fxmlPath));
            loader.setControllerFactory(applicationContext::getBean);
            Parent root = loader.load();

            Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();

            // Preserve the current window size instead of forcing the passed dimensions.
            double currentWidth = stage.getWidth();
            double currentHeight = stage.getHeight();
            double finalWidth = currentWidth > 0 ? currentWidth : width;
            double finalHeight = currentHeight > 0 ? currentHeight : height;

            stage.setScene(new Scene(root, finalWidth, finalHeight));
            stage.show();
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("Failed to load view: " + fxmlPath);
        }
    }

    /**
     * Navigates to a new view and returns its controller for data passing.
     *
     * <p>The current window size is preserved so the UI does not reset to a
     * default size when switching views.</p>
     *
     * @param <T> the type of the target controller
     * @param event the event triggering the navigation
     * @param fxmlPath the resource path to the target FXML file
     * @param width the preferred window width (used as fallback on first navigation)
     * @param height the preferred window height (used as fallback on first navigation)
     * @return the controller instance associated with the loaded view, or null if loading fails
     */
    public <T> T navigateToAndGetController(Event event, String fxmlPath, double width, double height) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(fxmlPath));
            loader.setControllerFactory(applicationContext::getBean);
            Parent root = loader.load();

            Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();

            // Preserve the current window size instead of forcing the passed dimensions.
            double currentWidth = stage.getWidth();
            double currentHeight = stage.getHeight();
            double finalWidth = currentWidth > 0 ? currentWidth : width;
            double finalHeight = currentHeight > 0 ? currentHeight : height;

            stage.setScene(new Scene(root, finalWidth, finalHeight));
            stage.show();

            return loader.getController();
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("Failed to load view with controller: " + fxmlPath);
            return null;
        }
    }
}