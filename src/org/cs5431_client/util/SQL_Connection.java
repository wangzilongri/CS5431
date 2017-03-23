package org.cs5431_client.util;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.*;
import java.util.ArrayList;

public class SQL_Connection {

    private static int port = 3306;
    private static String ip = "localhost";
    private static String DB_USER = "admin";
    private static String DB_PASSWORD = "$walaotwod0tseven$";

    public SQL_Connection(String ip, int port) {
        this.ip = ip;
        this.port = port;
    }

    public boolean isUniqueUsername(String username) {
        String url = "jdbc:mysql://" + ip + ":" + Integer.toString(port) + "/cs5431";

        System.out.println("Connecting to database...");

        try (Connection connection = DriverManager.getConnection(url, DB_USER, DB_PASSWORD)) {
            System.out.println("Database connected!");
            PreparedStatement verifyUniqueness = null;

            String checkUsername = "SELECT U.uid FROM Users U WHERE U.username = ?";
            verifyUniqueness = connection.prepareStatement(checkUsername);

            try {
                verifyUniqueness.setString(1, username);
                ResultSet rs = verifyUniqueness.executeQuery();
                if (rs.next()) {
                    System.out.println("The username is not unique");
                    return false;
                } else {
                    System.out.println("The username is unique");
                    return true;
                }
            } catch (SQLException e) {
                e.printStackTrace();
            } finally {
                if (verifyUniqueness != null) {
                    verifyUniqueness.close();
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    public JSONObject createUser(JSONObject user, String privKey, String pubKey) {

        String url = "jdbc:mysql://" + ip + ":" + Integer.toString(port) + "/cs5431";

        System.out.println("Connecting to database...");
        int uid = 0;
        JSONObject jsonUser = null;

        try (Connection connection = DriverManager.getConnection(url, DB_USER, DB_PASSWORD)) {
            System.out.println("Database connected!");
            PreparedStatement createUser = null;
            PreparedStatement createFolder = null;
            PreparedStatement createLog = null;
            PreparedStatement addPermission = null;

            String insertUser =  "INSERT INTO Users (uid, username, pwd, parentFolderid, email, privKey, pubKey)"
                    + " values (?, ?, ?, ?, ?, ?, ?)";
            String insertFolder = "INSERT INTO FileSystemObjects (fsoid, parentFolderid, fsoName, size, " +
                    "lastModified, isFile)"
                    + " values (?, ?, ?, ?, ?, ?)";
            String insertLog = "INSERT INTO UserLog (userLogid, uid, lastModified, actionType)"
                    + "values (?, ?, ?, ?)";
            String insertEditor = "INSERT INTO Editors (fsoid, uid) values (?, ?)";

            String username = (String) user.get("username");
            String pwd = (String) user.get("pwd");
            String email = null;
            if (user.has("email")) {
                email = (String) user.get("email");
            }

            try {
                connection.setAutoCommit(false);
                createFolder = connection.prepareStatement(insertFolder, Statement.RETURN_GENERATED_KEYS);
                createUser = connection.prepareStatement(insertUser, Statement.RETURN_GENERATED_KEYS);
                createLog = connection.prepareStatement(insertLog);
                addPermission = connection.prepareStatement(insertEditor);

                Timestamp currDate = new Timestamp(System.currentTimeMillis());
                createFolder.setInt (1, 0);
                createFolder.setInt (2, 1); //TODO: what to set as parent folder id?
                createFolder.setString (3, username);
                createFolder.setString   (4, Integer.toString(0));
                createFolder.setTimestamp (5, currDate);
                createFolder.setBoolean    (6, false);
                createFolder.executeUpdate();
                System.out.println("created folder");

                ResultSet rs = createFolder.getGeneratedKeys();
                rs.next();
                int folderid = rs.getInt(1);
                createUser.setInt (1, 0);
                createUser.setString (2, username);
                createUser.setString (3, pwd);
                createUser.setInt   (4, folderid);
                createUser.setString (5, email);
                createUser.setString    (6, privKey);
                createUser.setString    (7, pubKey);
                createUser.executeUpdate();
                System.out.println("created user");

                rs = createUser.getGeneratedKeys();
                rs.next();
                uid = rs.getInt(1);
                createLog.setInt(1, 0);
                createLog.setInt(2, uid);
                createLog.setTimestamp(3, currDate);
                createLog.setString(4, "CREATE_USER");
                createLog.executeUpdate();
                System.out.println("created log");

                addPermission.setInt(1, folderid);
                addPermission.setInt(2, uid);
                addPermission.executeUpdate();
                System.out.println("added owner as editor");

                connection.commit();

                jsonUser = new JSONObject();
                jsonUser.put("username", username);
                jsonUser.put("uid", uid);
                jsonUser.put("parentFolderid", folderid);
                jsonUser.put("email", email);
                jsonUser.put("privKey", privKey);
                jsonUser.put("pubKey", pubKey);

            } catch (SQLException e ) {
                e.printStackTrace();
                if (connection != null) {
                    try {
                        System.err.println("Transaction is being rolled back");
                        connection.rollback();
                    } catch(SQLException excep) {
                        excep.printStackTrace();
                    }
                }
            } finally {
                if (createFolder != null) {
                    createFolder.close();
                }
                if (createUser != null) {
                    createUser.close();
                }
                if (createLog != null) {
                    createLog.close();
                }
                if (addPermission != null) {
                    addPermission.close();
                }
                connection.setAutoCommit(true);
                return jsonUser;
            }

        } catch (SQLException e) {
            throw new IllegalStateException("Cannot connect the database!", e);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return jsonUser;
    }
    /** Adds fso to the db with sk = enc(secret key of fso). Adds owner as editor.
     * @Return fsoid of created fso **/
    public int createFso(JSONObject fso, String sk) {


        String url = "jdbc:mysql://" + ip + ":" + Integer.toString(port) + "/cs5431";

        System.out.println("Connecting to database...");
        int fsoid = 0;

        try (Connection connection = DriverManager.getConnection(url, DB_USER, DB_PASSWORD)) {

            System.out.println("Database connected!");
            int uid = fso.getInt("uid");
            int parentFolderid = fso.getInt("parentFolderid");
            String fsoName = fso.getString("fsoName");
            String size = fso.getString("size");
            Timestamp lastModified = (Timestamp) fso.get("lastModified");
            boolean isFile = fso.getBoolean("isFile");

            boolean hasPermission = verifyEditPermission(parentFolderid, uid);

            if (hasPermission) {
                PreparedStatement createFso = null;
                PreparedStatement addKey = null;
                PreparedStatement createLog = null;
                PreparedStatement addPermission = null;
                PreparedStatement addFile = null;

                String insertFolder = "INSERT INTO FileSystemObjects (fsoid, parentFolderid, fsoName, size, " +
                        "lastModified, isFile)"
                        + " values (?, ?, ?, ?, ?, ?)";
                String insertKey = "INSERT INTO FsoEncryption (fsoid, uid, encKey) values (?, ?, ?)";
                String insertLog = "INSERT INTO FileLog (fileLogid, fsoid, uid, lastModified, actionType)"
                        + "values (?, ?, ?, ?, ?)";
                String insertEditor = "INSERT INTO Editors (fsoid, uid) values (?, ?)";
                String insertFilePath = "INSERT INTO FileContents (fsoid, path) values (?,?)"; //TODO: how to get path

                try {
                    connection.setAutoCommit(false);
                    createFso = connection.prepareStatement(insertFolder, Statement.RETURN_GENERATED_KEYS);
                    createLog = connection.prepareStatement(insertLog);
                    addPermission = connection.prepareStatement(insertEditor);
                    addKey = connection.prepareStatement(insertKey);

                    createFso.setInt(1, 0);
                    createFso.setInt(2, parentFolderid);
                    createFso.setString(3, fsoName);
                    createFso.setString(4, size);
                    createFso.setTimestamp(5, lastModified);
                    createFso.setBoolean(6, isFile);
                    createFso.executeUpdate();
                    System.out.println("created folder");

                    ResultSet rs = createFso.getGeneratedKeys();
                    rs.next();
                    fsoid = rs.getInt(1);

                    addKey.setInt(1, fsoid);
                    addKey.setInt(2, uid);
                    addKey.setString(3, sk);
                    addKey.executeUpdate();
                    System.out.println("added added sk");

                    String actionType;
                    if (isFile) {
                        actionType = "UPLOAD_FILE";
                    } else {
                        actionType = "CREATE_FOLDER";
                    }
                    createLog.setInt(1, 0);
                    createLog.setInt(2, fsoid);
                    createLog.setInt(3, uid);
                    createLog.setTimestamp(4, lastModified);
                    createLog.setString(5, actionType);
                    createLog.executeUpdate();
                    System.out.println("created log");

                    addPermission.setInt(1, fsoid);
                    addPermission.setInt(2, uid);
                    addPermission.executeUpdate();
                    System.out.println("added owner as editor");

                    if (isFile) {
                        addFile = connection.prepareStatement(insertFilePath);
                        addFile.setInt(1, fsoid);
                        addFile.setString(2, "path");
                        addFile.executeUpdate();
                        System.out.println("added file path");
                    }

                    connection.commit();

                } catch (SQLException e) {
                    e.printStackTrace();
                    if (connection != null) {
                        try {
                            System.err.println("Transaction is being rolled back");
                            connection.rollback();
                        } catch (SQLException excep) {
                            excep.printStackTrace();
                        }
                    }
                } finally {
                    if (createFso != null) {
                        createFso.close();
                    }
                    if (createLog != null) {
                        createLog.close();
                    }
                    if (addPermission != null) {
                        addPermission.close();
                    }
                    connection.setAutoCommit(true);
                    return fsoid;
                }
            }

        } catch (SQLException e) {
            throw new IllegalStateException("Cannot connect the database!", e);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return -1;
    }

    /** Compares username and encrypted password with row of User table.
     * @Return h(privKey) of the user if the authentication is valid. **/
    public JSONObject authenticate(JSONObject allegedUser) {

        String url = "jdbc:mysql://" + ip + ":" + Integer.toString(port) + "/cs5431";

        System.out.println("Connecting to database...");
        JSONObject user = new JSONObject();

        try (Connection connection = DriverManager.getConnection(url, DB_USER, DB_PASSWORD)) {
            System.out.println("Database connected!");
            PreparedStatement verifyUser = null;

            String checkPassword = "SELECT U.uid, U.parentFolderid, U.email, U.privKey, U.pubKey" +
                    " FROM Users U WHERE U.username = ? AND U.pwd = ?";
            verifyUser = connection.prepareStatement(checkPassword);

            String username = allegedUser.getString("username");
            String encPwd = allegedUser.getString("pwd");
            //TODO: salting?

            try {
                verifyUser.setString(1, username);
                verifyUser.setString(2, encPwd);
                ResultSet rs = verifyUser.executeQuery();

                if (rs.next()) {
                    //user valid
                    System.out.println("Valid user");
                    int uid = rs.getInt(1);
                    int parentFolderid = rs.getInt(2);
                    String email = rs.getString(3);
                    String privKey = rs.getString(4);
                    String pubKey = rs.getString(5);
                    user.put("uid", uid);
                    user.put("parentFolderid", parentFolderid);
                    user.put("email", email);
                    user.put("privKey", privKey);
                    user.put("pubKey", pubKey);
                    return user;
                } else {
                    return null;
                }
            } catch (JSONException e) {
                e.printStackTrace();
            } finally {
                if (verifyUser != null) {
                    verifyUser.close();
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        } catch (JSONException e) {
            e.printStackTrace();
        }
        //TODO: log the number of failed authentications?
        System.out.println("Invalid user");
        return null;
    }

    /** Gets the id, enc(name), size, last modified and isFile that has parentFolderid as a parent.
     * @Return An array of JsonObjects of all childrens  **/
    public static ArrayList<JSONObject> getChildren(int parentFolderid, int uid) {

        boolean hasPermission = verifyEditPermission(parentFolderid, uid);
        if (hasPermission) {
            ArrayList<JSONObject> files = new ArrayList<>();
            String url = "jdbc:mysql://" + ip + ":" + Integer.toString(port) + "/cs5431";

            System.out.println("Connecting to database...");

            try (Connection connection = DriverManager.getConnection(url, DB_USER, DB_PASSWORD)) {
                System.out.println("Database connected!");
                PreparedStatement getFiles = null;

                String selectFiles = "SELECT F.fsoid, F.fsoName, F.size, F.lastModified, F.isFile " +
                        "FROM FileSystemObjects F " +
                        "WHERE F.parentFolderid = ? AND EXISTS" +
                        "(SELECT * FROM Editors E WHERE E.uid=?) OR EXISTS" +
                        "(SELECT * FROM Viewers V WHERE V.uid=?);";
                getFiles = connection.prepareStatement(selectFiles);
                //TODO: get enc key as well

                try {
                    getFiles.setInt(1, parentFolderid);
                    getFiles.setInt(2, uid);
                    getFiles.setInt(3, uid);
                    ResultSet rs = getFiles.executeQuery();
                    while (rs.next()) {
                        JSONObject fso = new JSONObject();
                        fso.put("id", rs.getInt(1));
                        fso.put("name", rs.getString(2));
                        fso.put("size", rs.getString(3));
                        fso.put("lastModified", rs.getTimestamp(4));

                        if (rs.getBoolean(5)) {
                            fso.put("FSOType", "FILE");
                        } else {
                            fso.put("FSOType", "FOLDER");
                        }
                        files.add(fso);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    if (getFiles != null) {
                        getFiles.close();
                    }
                    return files;
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
        System.out.println("User does not have permission to edit");
        return null;
    }

    public java.io.File getFile(int fsoid) {
        //TODO: get file content
        return new java.io.File("pathname");
    }

    /** Gets all viewers and editors of the fso. Fsoid has to refer to an existing fso.
     * @Return A JsonObjects with 2 fields: "editors" and "viewers" with a arraylist value; returns null otherwise  **/
    public static JSONObject getPermissions(int fsoid) {
        String url = "jdbc:mysql://" + ip + ":" + Integer.toString(port) + "/cs5431";

        System.out.println("Connecting to database...");

        try (Connection connection = DriverManager.getConnection(url, DB_USER, DB_PASSWORD)) {
            System.out.println("Database connected!");
            PreparedStatement verifyEditors = null;
            PreparedStatement verifyViewers = null;

            String selectEditors = "SELECT E.uid FROM Editors E WHERE E.fsoid = ?";
            String selectViewers = "SELECT V.uid FROM Viewers V WHERE V.fsoid = ?";
            verifyEditors = connection.prepareStatement(selectEditors);
            verifyViewers = connection.prepareStatement(selectViewers);

            ArrayList<Integer> editors = new ArrayList<Integer>();
            ArrayList<Integer> viewers = new ArrayList<Integer>();

            try {
                verifyEditors.setInt(1, fsoid);
                ResultSet editorsId = verifyEditors.executeQuery();

                verifyViewers.setInt(1, fsoid);
                ResultSet viewersId = verifyViewers.executeQuery();

                while (editorsId.next()) {
                    int editor = editorsId.getInt(1);
                    editors.add(editor);
                }
                while (editorsId.next()) {
                    int viewer = viewersId.getInt(1);
                    viewers.add(viewer);
                }
                JSONObject permissions = new JSONObject();
                permissions.put("editors", editors);
                permissions.put("viewers", viewers);
                return permissions;

            } catch (JSONException e) {
                e.printStackTrace();
            } finally {
                if (verifyEditors != null) {
                    verifyEditors.close();
                }
                if (verifyViewers != null) {
                    verifyViewers.close();
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return null;
    }

    public static boolean verifyEditPermission(int fsoid, int uid) {
        JSONObject permissions = getPermissions(fsoid);
        boolean hasPermission = false;
        if (permissions != null) {
            try {
                JSONArray editors = permissions.getJSONArray("editors");
                for (int i = 0; i < editors.length(); i++) {
                    int editorid = (int) editors.get(i);
                    if (editorid == uid) {
                        hasPermission = true;
                    }
                }
            } catch (JSONException e1) {
                System.out.println("Unable to parse JSON object.");
                e1.printStackTrace();
            }
        }
        return hasPermission;
    }

    public static boolean verifyBothPermission(int fsoid, int uid) {
        JSONObject permissions = getPermissions(fsoid);
        boolean hasPermission = false;
        if (permissions != null) {
            try {
                JSONArray viewers = permissions.getJSONArray("viewers");
                for (int i = 0; i < viewers.length(); i++) {
                    int viewerid = (int) viewers.get(i);
                    if (viewerid == uid) {
                        hasPermission = true;
                    }
                }
                JSONArray editors = permissions.getJSONArray("editors");
                for (int i = 0; i < editors.length(); i++) {
                    int editorid = (int) viewers.get(i);
                    if (editorid == uid) {
                        hasPermission = true;
                    }
                }
            } catch (JSONException e1) {
                System.out.println("Unable to parse JSON object.");
                e1.printStackTrace();
            }
        }
        return hasPermission;
    }

    /** Checks the permissions of the uid before getting all file log entries of this fsoid.
     * @Return A JsonArray of filelog entries; returns null otherwise  **/
    public static JSONArray getFileLog(int fsoid, int uid) {
        boolean hasPermission = verifyBothPermission(fsoid, uid);
        if (hasPermission) {
            System.out.println("Can view file logs");
            String url = "jdbc:mysql://" + ip + ":" + Integer.toString(port) + "/cs5431";
            PreparedStatement getFileLog = null;

            System.out.println("Connecting to database...");

            try (Connection connection = DriverManager.getConnection(url, DB_USER, DB_PASSWORD)) {
                System.out.println("Database connected!");

                String selectLog = "SELECT L.uid, L.lastModified, L.actionType FROM FileLog L WHERE L.fsoid = ?";
                getFileLog = connection.prepareStatement(selectLog);
                JSONArray fileLogArray = new JSONArray();

                try {
                    getFileLog.setInt(1, fsoid);
                    ResultSet rs = getFileLog.executeQuery();

                    while (rs.next()) {
                        JSONObject log = new JSONObject();
                        log.put("uid", rs.getInt(1));
                        log.put("lastModified", rs.getTimestamp(2));
                        log.put("actionType", rs.getString(3));
                        fileLogArray.put(log);
                    }

                } catch (SQLException e) {
                    e.printStackTrace();
                } catch (JSONException e) {
                    e.printStackTrace();
                } finally {
                    if (getFileLog != null) {
                        getFileLog.close();
                    }
                    return fileLogArray;
                }
        } catch (SQLException e) {
                e.printStackTrace();
            }
        }
        return null;
    }

    public static int renameFso(int fsoid, int uid, String newName) {
        boolean hasPermission = verifyEditPermission(fsoid, uid);
        if (hasPermission) {
            System.out.println("Can rename fso");
            String url = "jdbc:mysql://" + ip + ":" + Integer.toString(port) + "/cs5431";
            PreparedStatement renameFso = null;

            System.out.println("Connecting to database...");

            try (Connection connection = DriverManager.getConnection(url, DB_USER, DB_PASSWORD)) {
                System.out.println("Database connected!");

                String updateName = "UPDATE FileSystemObjects SET fsoName = ? WHERE fsoid =  ?";
                renameFso = connection.prepareStatement(updateName);

                try {
                    renameFso.setString(1, newName);
                    renameFso.setInt(2, fsoid);
                    renameFso.executeUpdate();

                } catch (SQLException e) {
                    e.printStackTrace();
                } finally {
                    if (renameFso != null) {
                        renameFso.close();
                    }
                    return fsoid;
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
        return -1;
    }

    public static void main(String[] args) {
        //Connection connection = connectToDB();
        System.out.print(renameFso(54, 22, "changename??"));
    }

}

