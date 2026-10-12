package matchmaking;

import java.util.Properties;

public final class WorkflowRepository {
    private final Store store;
    public WorkflowRepository(Store store) { this.store = store; }
    public Workflow find(String id) {
        Properties p = store.read("workflow", id);
        if (p.isEmpty()) return null;
        return new Workflow(id, p.getProperty("player"), Integer.parseInt(p.getProperty("rating")),
                Integer.parseInt(p.getProperty("partySize")), Integer.parseInt(p.getProperty("ping")),
                Boolean.parseBoolean(p.getProperty("suspicious")), Workflow.Status.valueOf(p.getProperty("status")));
    }
    public void save(Workflow flow) {
        Properties p = new Properties();
        p.setProperty("player", flow.player());
        p.setProperty("rating", Integer.toString(flow.rating()));
        p.setProperty("partySize", Integer.toString(flow.partySize()));
        p.setProperty("ping", Integer.toString(flow.ping()));
        p.setProperty("suspicious", Boolean.toString(flow.suspicious()));
        p.setProperty("status", flow.status().name());
        store.write("workflow", flow.id(), p);
    }
}
