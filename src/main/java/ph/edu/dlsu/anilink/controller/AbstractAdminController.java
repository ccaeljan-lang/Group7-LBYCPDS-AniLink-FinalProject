package ph.edu.dlsu.anilink.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.scene.control.Alert;
import org.springframework.stereotype.Controller;

import ph.edu.dlsu.anilink.service.SupabaseService;
import ph.edu.dlsu.anilink.util.UserSession;
import ph.edu.dlsu.anilink.util.ViewNavigator;

/**
 * Abstract base class for admin controllers.
 *
 * <p>Extracts shared functionality (alert dialogs, navigation handlers,
 * and common Supabase loaders) so that {@link ScheduleManagementController}
 * and {@link TripManagementController} do not duplicate the same code.</p>
 */
public abstract class AbstractAdminController {

    protected final SupabaseService supabaseService;
    protected final UserSession userSession;
    protected final ViewNavigator viewNavigator;
    protected final ObjectMapper objectMapper;

    public AbstractAdminController(
            SupabaseService supabaseService,
            UserSession userSession,
            ViewNavigator viewNavigator) {
        this.supabaseService = supabaseService;
        this.userSession = userSession;
        this.viewNavigator = viewNavigator;

        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    /**
     * Shows an alert dialog on the JavaFX Application Thread.
     */
    protected void showAlert(
            Alert.AlertType type,
            String title,
            String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(type);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(message);
            alert.showAndWait();
        });
    }

    /**
     * Shows an inline status message on a Label.
     */
    protected void showInlineStatus(
            javafx.scene.control.Label statusLabel,
            String message,
            String colorCode) {
        Platform.runLater(() -> {
            statusLabel.setText(message);
            statusLabel.setStyle(
                    "-fx-text-fill: " + colorCode
                            + "; -fx-font-weight: bold;"
                            + " -fx-font-size: 13px;"
            );
        });
    }

    // --- Navigation Handlers ---

    protected void navigateToDashboard(ActionEvent event, double width, double height) {
        viewNavigator.navigateTo(event, "/fxml/AdminDashboard.fxml", width, height);
    }

    protected void navigateToRouteManagement(ActionEvent event, double width, double height) {
        viewNavigator.navigateTo(event, "/fxml/RouteManagement.fxml", width, height);
    }

    protected void navigateToScheduleManagement(ActionEvent event, double width, double height) {
        viewNavigator.navigateTo(event, "/fxml/ScheduleManagement.fxml", width, height);
    }

    protected void navigateToTripManagement(ActionEvent event, double width, double height) {
        viewNavigator.navigateTo(event, "/fxml/TripManagement.fxml", width, height);
    }

    protected void navigateToLogout(ActionEvent event, double width, double height) {
        userSession.clearSession();
        viewNavigator.navigateTo(event, "/fxml/Login.fxml", width, height);
    }
}
