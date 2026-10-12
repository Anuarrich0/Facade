package matchmaking;

public final class WorkflowRepository {
    private final Store store;
    public WorkflowRepository(Store store) { this.store = store; }
    public Workflow find(String id) {
        return store.read("workflow", id, Workflow.class);
    }
    public void save(Workflow flow) {
        store.write("workflow", flow.id(), flow);
    }
}
