public class App {
    public static void main(String[] args) {
        TasksDAO realisation = new Backend();
        UI.Launch(args, realisation);
    }
}
