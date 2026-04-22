package AutoNego;

import jade.core.Profile;
import jade.core.ProfileImpl;
import jade.core.Runtime;
import jade.wrapper.ContainerController;
import javax.swing.SwingUtilities;

public final class AutoNegoDemoLauncher {

    public static void main(String[] args) {
        Runtime runtime = Runtime.instance();
        runtime.setCloseVM(true);

        Profile profile = new ProfileImpl();
        profile.setParameter(Profile.GUI, "false");

        ContainerController container = runtime.createMainContainer(profile);

        SwingUtilities.invokeLater(() -> {
            try {
                LauncherControlGui launcherGui = new LauncherControlGui(container);
                launcherGui.setVisible(true);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }
}