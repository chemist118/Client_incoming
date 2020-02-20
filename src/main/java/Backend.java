import javafx.collections.FXCollections;
import javafx.scene.control.DatePicker;

import java.io.*;
import java.net.Socket;
import java.net.SocketException;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@SuppressWarnings("unchecked")
public class Backend implements TasksDAO {
    // IO Streams
    ObjectOutputStream objectToServer = null;
    ObjectInputStream objectFromServer = null;
    DataInputStream dataFromServer = null;
    DataOutputStream dataToServer = null;
    // Host name or ip
    Socket socket;
    CopyOnWriteArrayList<Task> Info = new CopyOnWriteArrayList<>();

    @Override
    public Boolean setSocket(String host, Integer port) {
        try {
            this.socket = new Socket(host, port); // set socket
            Info = Load();
            return true; // Connected
        } catch (IOException ex) {
            return false; // Can't connect to server
        }
    }

    @Override
    public CopyOnWriteArrayList Load() {
        CopyOnWriteArrayList<Task> temp = new CopyOnWriteArrayList<>();
        try {
            // Send a string command to the server
            dataToServer = new DataOutputStream(socket.getOutputStream()); // Create an output stream
            dataToServer.writeUTF("LOAD_INFO");
            dataToServer.flush();
            objectFromServer = new ObjectInputStream(socket.getInputStream());
            temp = (CopyOnWriteArrayList<Task>) objectFromServer.readObject();
        } catch (SocketException ex) {
            System.out.println("Server offline");
        } catch (IOException | ClassNotFoundException ex) {
            ex.printStackTrace();
        }
        return temp;
    }

    @Override
    public Boolean Synchronize() {
        Info = Load();
        return true;
    }

    @Override
    public CopyOnWriteArrayList<Task> getAll() {
        return Info;
    }

    @Override
    public Task getTask(int index) {
        return Info.get(index);
    }

    @Override
    public Task Add(Task task) {
        try {
            // Send a string command to the server
            dataToServer = new DataOutputStream(socket.getOutputStream()); // Create an output stream
            dataToServer.writeUTF("ADD_TASK");
            dataToServer.flush();
            // Send a Task
            objectToServer = new ObjectOutputStream(socket.getOutputStream());
            objectToServer.writeObject(task);
            // Get fixed task
            objectFromServer = new ObjectInputStream(socket.getInputStream());
            task = (Task) objectFromServer.readObject();
        } catch (IOException | ClassNotFoundException ex) {
            ex.printStackTrace();
        }
        return task;
    }

    @Override
    public void Remove(Task task) {
        try {
            // Send a string command to the server
            dataToServer = new DataOutputStream(socket.getOutputStream()); // Create an output stream
            dataToServer.writeUTF("REMOVE");
            dataToServer.flush();
            // Send a Task id
            dataToServer.writeInt(task.id);
            // Get answer
            dataFromServer = new DataInputStream(socket.getInputStream());
            boolean isOK = dataFromServer.readBoolean();
            if (isOK)
                task.setArchived(true);
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    @Override
    public void Update(Task newTask) {
        try {
            // Send a string command to the server
            dataToServer = new DataOutputStream(socket.getOutputStream()); // Create an output stream
            dataToServer.writeUTF("UPDATE_TASK");
            dataToServer.flush();
            // Send a Task
            objectToServer = new ObjectOutputStream(socket.getOutputStream());
            objectToServer.writeObject(newTask);
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    @Override
    public ArrayList<Task> filter(
            Boolean isNotEnded,
            Boolean isEnded,
            Boolean isAll,
            Boolean FireTasks,
            String tags,
            String Description,
            int numOfSpecialFilter) {
        ArrayList<Task> tempInfo = Info.stream().filter(x -> !x.getArchived()).collect(Collectors.toCollection(ArrayList::new));
        Predicate<Task> typeOfTasks = x -> true;
        Predicate<Task> isFireTask = x -> true;
        Predicate<Task> haveDescription = x -> true;
        Predicate<Task> haveTags = x -> true;
        Predicate<Task> specialFilter = x -> true;

        if (isNotEnded) { // неФП
            typeOfTasks = x -> !x.getDone(); // ФП
        }

        if (isEnded) // неФП
            typeOfTasks = Mission::getDone; // ФП

        if (isAll) // неФП
            typeOfTasks = x -> true; // ФП

        if (FireTasks) // неФП
            isFireTask = x -> x.getDate().getValue().isBefore(LocalDate.now().plus(1, ChronoUnit.WEEKS)); // ФП

        if (!tags.trim().isEmpty()) { // неФП
            haveTags = task ->
                    Stream.of(tags.split(","))
                            .allMatch(userTag -> task.tags.stream().anyMatch(tag -> tag.equalsIgnoreCase(userTag))); // ФП
        }


        if (!Description.trim().isEmpty()) { // неФП
            haveDescription = task ->
                    task.description.toUpperCase().contains(Description.trim().toUpperCase()); // ФП
        }

        Comparator<DatePicker> comparator = (o1, o2) -> {
            if (o1.getValue().isBefore(o2.getValue())) return -1;  //  неФП
            if (o1.getValue().equals(o2.getValue())) return 0;
            return 1;
        };

        tempInfo = tempInfo.parallelStream() // ФП
                .filter(typeOfTasks)
                .filter(isFireTask)
                .filter(haveDescription)
                .filter(haveTags)
                .collect(Collectors.toCollection(ArrayList::new));

        switch (numOfSpecialFilter) {
            case 1: { //ФП
                specialFilter = x -> x.getDate().getValue().isBefore(LocalDate.now().plus(1, ChronoUnit.MONTHS))
                        && x.getDate().getValue().isAfter(LocalDate.now().minus(1, ChronoUnit.DAYS));
                break;
            }
            case 2: { //ФП
                specialFilter = task ->
                        task.subtasks.stream().filter(Mission::getDone).count() >= (double) task.subtasks.size() / 2
                                && task.subtasks.size() != 0;
                break;
            }
            case 3: {
                Map<String, Integer> tagsRating = new HashMap<>();
                tempInfo.forEach(task ->  //ФП
                        task.tags.forEach(tag -> {
                            int count = Optional.ofNullable(tagsRating.get(tag.toUpperCase())).orElse(0);
                            tagsRating.remove(tag.toUpperCase());
                            tagsRating.put(tag.toUpperCase(), ++count);
                        }));
                List<String> mostPopularTags =
                        tagsRating.entrySet().stream()  //ФП
                                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                                .limit(3)
                                .map(Map.Entry::getKey)
                                .collect(Collectors.toList());
                specialFilter = task ->  //ФП
                        task.getDate().getValue().isBefore(LocalDate.now()) &&
                                mostPopularTags.stream()
                                        .anyMatch(userTag -> task.tags.stream()
                                                .anyMatch(tag -> tag.equalsIgnoreCase(userTag))); // ФП
                break;
            }
            case 4: {  // ФП
                tempInfo.removeIf(x -> x.getDate().getValue().isBefore(LocalDate.now()));
                tempInfo = tempInfo.stream().sorted((o1, o2) -> comparator.compare(o1.getDate(), o2.getDate()))
                        .limit(3)
                        .collect(Collectors.toCollection(ArrayList::new));
                break;
            }
            case 5: {  // ФП
                tempInfo.removeIf(x -> !x.tags.isEmpty() || !x.haveDate);
                tempInfo = tempInfo.stream().sorted((o1, o2) -> comparator.reversed().compare(o1.getDate(), o2.getDate()))
                        .limit(4)
                        .collect(Collectors.toCollection(ArrayList::new));
            }
        }

        tempInfo = tempInfo.stream() // ФП
                .filter(specialFilter)
                .collect(Collectors.toCollection(ArrayList::new));

        return tempInfo;
    }
}
