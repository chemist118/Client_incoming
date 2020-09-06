import javafx.collections.FXCollections;
import javafx.scene.control.DatePicker;

import java.io.*;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
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
            socket.setSoTimeout(5000);
            Load();
            return true; // Connected
        } catch (IOException ex) {
            return false; // Can't connect to server
        }
    }

    @Override
    public void reconnect() {
        try {
            this.socket = new Socket(socket.getInetAddress().getHostName(), socket.getPort()); // set socket
            socket.setSoTimeout(5000);
        } catch (IOException ignored) { // Can't connect to server
        }
    }

    @Override
    public synchronized Boolean Load() {
        CopyOnWriteArrayList<Task> temp;
        try {
            // Send a string command to the server
            dataToServer = new DataOutputStream(socket.getOutputStream()); // Create an output stream
            dataToServer.writeUTF("LOAD_INFO");
            dataToServer.flush();
            objectFromServer = new ObjectInputStream(socket.getInputStream());
            temp = (CopyOnWriteArrayList<Task>) objectFromServer.readObject();
        } catch (SocketException | SocketTimeoutException ex) {
            System.out.println("OnLoad: Server offline");
            reconnect();
            return false;
        } catch (IOException | ClassNotFoundException ex) {
            System.out.println("OnLoad: Server off");
            reconnect();
            return false;
        }
        Info = temp;
        return true;
    }

    @Override
    public synchronized Boolean Synchronize() {
        boolean SyncNeeded = false;
        CopyOnWriteArrayList<Task> oldInfo = Info;
        if (!Load()) {
            reconnect();
            return false;
        }
        if (oldInfo.size() == Info.size()) {
            for (int i = 0; i < oldInfo.size(); i++) {
                SyncNeeded = SyncNeeded | !oldInfo.get(i).equals(Info.get(i));
            }
        } else SyncNeeded = true;
        return SyncNeeded;
    }

    @Override
    public CopyOnWriteArrayList<Task> getAll() {
        return Info;
    }

    @Override
    public Task getTask(int id) {
        for (Task t : Info)
            if (t.id == id) return t;
        return Info.get(0);
    }

    @Override
    public synchronized Boolean Add(Task task) {
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
            return true;
        } catch (SocketException | SocketTimeoutException ex) {
            System.out.println("OnAdd: Server offline, Timeout");
            reconnect();
            return false;
        } catch (IOException | ClassNotFoundException ex) {
            System.out.println("OnAdd: Server offline");
            reconnect();
            return false;
        }
    }

    @Override
    public synchronized Boolean Remove(Task task) {
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
        } catch (SocketException ex) {
            System.out.println("OnRemove: Server is Offline");
            reconnect();
            return false;
        } catch (SocketTimeoutException ex) {
            System.out.println("OnRemove: Timeout");
            reconnect();
            return false;
        } catch (IOException ex) {
            reconnect();
            return false;
        }
        return true;
    }

    @Override
    public synchronized Boolean Update(Task newTask) {
        try {
            // Send a string command to the server
            dataToServer = new DataOutputStream(socket.getOutputStream()); // Create an output stream
            dataToServer.writeUTF("UPDATE_TASK");
            dataToServer.flush();
            // Send a Task
            objectToServer = new ObjectOutputStream(socket.getOutputStream());
            objectToServer.writeObject(newTask);
        } catch (SocketException ex) {
            System.out.println("OnUpdate: Server offline");
            reconnect();
            return false;
        } catch (SocketTimeoutException ex) {
            System.out.println("OnUpdate: Timeout");
            reconnect();
            return false;
        } catch (IOException ex) {
            reconnect();
            return false;
        }
        return true;
    }

    @Override
    public CopyOnWriteArrayList<Task> filter(String tag, String Description, int numOfFilter) {
        CopyOnWriteArrayList<Task> temp = new CopyOnWriteArrayList<>();
        try {
            // Send a string command to the server
            dataToServer = new DataOutputStream(socket.getOutputStream()); // Create an output stream
            dataToServer.writeUTF("FILTER");
            dataToServer.flush();
            dataToServer = new DataOutputStream(socket.getOutputStream()); // Create an output stream
            dataToServer.writeInt(numOfFilter);
            dataToServer.writeUTF(tag);
            dataToServer.writeUTF(Description);
            dataToServer.flush();
            // Get filtered list
            objectFromServer = new ObjectInputStream(socket.getInputStream());
            temp = (CopyOnWriteArrayList<Task>) objectFromServer.readObject();
        } catch (SocketException | SocketTimeoutException ex) {
            System.out.println("OnFilter: Server offline, Timeout");
            reconnect();
            return null;
        } catch (IOException | ClassNotFoundException ex) {
            System.out.println("OnFilter: Server offline");
            reconnect();
            return null;
        }
        return temp;
    }
}
