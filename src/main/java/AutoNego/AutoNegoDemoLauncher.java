package AutoNego;

import jade.core.Profile;
import jade.core.ProfileImpl;
import jade.core.Runtime;
import jade.wrapper.AgentController;
import jade.wrapper.ContainerController;
import jade.wrapper.StaleProxyException;

public final class AutoNegoDemoLauncher {
    private AutoNegoDemoLauncher() {
    }

    public static void main(String[] args) {
        Runtime runtime = Runtime.instance();
        runtime.setCloseVM(true);

        Profile profile = new ProfileImpl();
        profile.setParameter(Profile.GUI, "true");

        ContainerController container = runtime.createMainContainer(profile);
        try {
            startAgent(container, "broker", SimpleBrokerAgent.class.getName());
            startAgent(container, "dealer", SimpleDealerAgent.class.getName());
            startAgent(container, "buyer", SimpleBuyerAgent.class.getName());
        } catch (StaleProxyException exception) {
            throw new IllegalStateException("Failed to start AutoNego demo agents.", exception);
        }
    }

    private static void startAgent(ContainerController container, String localName, String className)
            throws StaleProxyException {
        AgentController agent = container.createNewAgent(localName, className, new Object[0]);
        agent.start();
    }
}
