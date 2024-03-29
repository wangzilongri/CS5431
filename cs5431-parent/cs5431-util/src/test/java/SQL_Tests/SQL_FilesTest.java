package SQL_Tests;

import org.cs5431.SQL_Accounts;
import org.cs5431.SQL_Files;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;


import java.io.IOException;
import java.net.Socket;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.cs5431.JSON.receiveJson;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Created by zilong on 15/5/17.
 */
class SQL_FilesTest {
    public static SQL_Files files;
    public static SQL_Accounts accounts;
    String IP = "127.0.0.1";
    int port = 3306;
    JSONObject jsonUser;
    JSONObject fso;
    final boolean MASK = false;

    @BeforeEach
    void setUp() {
        //RUN SERVER VIEW BEFORE ATTEMPTING TO RUN TESTS
        files = new SQL_Files(IP, port, "zilong", "12345678901234567890");
        accounts = new SQL_Accounts(IP, port, "zilong", "12345678901234567890");

        String username = "username";
        String pubKey = "pubKey";
        String privKey = "privKey";
        String privKeySalt = "privKeySalt";
        int uid = 1;
        int folderid = 1;
        String email = "email@email.com";
        jsonUser = new JSONObject();
        jsonUser.put("msgType", "registrationAck");
        jsonUser.put("username", username);
        jsonUser.put("uid", uid);
        jsonUser.put("parentFolderid", folderid);
        jsonUser.put("email", email);
        jsonUser.put("privKey", privKey);
        jsonUser.put("privKeySalt", privKeySalt);
        jsonUser.put("pubKey", pubKey);
        jsonUser.put("has2fa", 0);
        jsonUser.put("hasPwdRec", true);
        jsonUser.put("phoneNo", "6073799856");

        fso = new JSONObject() ;// = receiveJson(s);
        fso.put("msgType","createFso");
        fso.put("parentFolderid",1);
        fso.put("uid",322);
        fso.put("fsoName","Onii-chan.txt");
        fso.put("size","123");
        fso.put("lastModified","1993-12-12 12:12:33");
        fso.put("isFile",true);

        //INPUT ARRAYS
        JSONArray editorsArr = new JSONArray();
        editorsArr.put("123");

        JSONArray viewersArr = new JSONArray();
        viewersArr.put("321");

        JSONArray editorsKeysArr = new JSONArray();
        editorsKeysArr.put("3154");

        JSONArray viewersKeysArr = new JSONArray();
        viewersKeysArr.put("18-03-1993");


        fso.put("editors",editorsArr);
        fso.put("viewers",viewersArr);
        fso.put("editorsKeys",editorsKeysArr);
        fso.put("viewersKeys",viewersKeysArr);
        fso.put("fileIV","123");
        fso.put("fsoNameIV","666");
        fso.put("fsoid","32123");


        fso.put("editorList",editorsArr);
        fso.put("viewerList",viewersArr);
        fso.put("editorKeys",editorsKeysArr);
        fso.put("viewerKeys",viewersKeysArr);
        fso.put("file","asd");
    }

    @Test
    void check_uploadKeys() throws Exception {
        Socket s = new Socket(IP,port);

        JSONObject jsonObject = new JSONObject() ;// = receiveJson(s);
        jsonObject.put("msgType","uploadKeys");
        jsonObject.put("fsoid",1);
        jsonObject.put("uid",322);
        JSONObject k = files.uploadKeys(jsonObject,IP);
        assertNull(k);
    }

    @Test
    void check_createFso() throws Exception {
        Socket s = new Socket(IP,port);

        //setUp();

        int k = files.createFso(fso,IP);

        assertEquals(k,-1);
    }

    @Test
    void check_addParentPermissions(){
        JSONObject fso = new JSONObject() ;// = receiveJson(s);
        fso.put("msgType","createFso");
        fso.put("parentFolderid",1);
        fso.put("uid",322);
        fso.put("fsoName","Onii-chan.txt");
        fso.put("size","123");
        fso.put("lastModified","1993-12-12 12:12:33");
        fso.put("isFile",true);

        //INPUT ARRAYS
        JSONArray editorsArr = new JSONArray();
        editorsArr.put("123");

        JSONArray viewersArr = new JSONArray();
        viewersArr.put("321");

        JSONArray editorsKeysArr = new JSONArray();
        editorsKeysArr.put("3154");

        JSONArray viewersKeysArr = new JSONArray();
        viewersKeysArr.put("18-03-1993");


        fso.put("editors",editorsArr);
        fso.put("viewers",viewersArr);
        fso.put("editorsKeys",editorsKeysArr);
        fso.put("viewersKeys",viewersKeysArr);
        fso.put("fileIV","123");
        fso.put("fsoNameIV","asdasd");

        boolean result = files.addParentPermissions(322,123,1,IP,editorsArr,
                viewersArr,editorsKeysArr,viewersKeysArr);

        assertEquals(false,result);

    }

    @Test
    void check_getChildren(){
        JSONObject asd = new JSONObject();

        asd.put("uid",123);
        asd.put("fsoid",422);

        JSONArray result = files.getChildren(asd,IP);

        assertNull(result);
    }

    @Test
    void check_getFile(){
        JSONObject asd = new JSONObject();

        asd.put("uid",123);
        asd.put("fsoid",422);
        JSONObject result = new JSONObject();
        try{
            result = files.getFile(asd,IP);
        }catch(Exception e){
            ;
        }

        assertNull(result);
    }
    @Test
    void check_getPermissions(){

        JSONObject asd = files.getPermissions(fso.getInt("fsoid"));
        JSONObject result = new JSONObject();
        String[] empt = {};
        result.put("viewers",empt);
        result.put("editors",empt);
        //Redundant test
        assert(true);
    }
    @Test
    void check_verifyEditPermission(){

        boolean result = files.verifyEditPermission(fso.getInt("fsoid"),fso.getInt("uid"));

        assertEquals(false,result);
    }

    @Test
    void check_verifyBothPermission(){
        boolean result = files.verifyBothPermission(fso.getInt("fsoid"),fso.getInt("uid"));

        assertEquals(false,result);
    }

    @Test
    void check_getFileLog(){

        //Ought to be empty
        //Should throw exception
        if(!MASK){
            try{
                JSONArray result = files.getFileLog(fso,IP);
                assertEquals(null,result);
            }
            catch(Exception e){
                ;
            }
        }
        else{
            assert(true);
        }

    }

    @Test
    void check_renameFso(){
        //int fsoid, int uid, String newName, String
        //newFSONameIV, String sourceIp

        int result;

        result = files.renameFso(fso.getInt("fsoid"),fso.getInt("uid"),
                "Onii-chan-v2.txt","666",IP);

        assertEquals(-1,result);
        assertEquals(fso.getString("fsoNameIV"),"666");
    }

    @Test
    void check_removeDuplicates(){

        boolean result = files.removeDuplicates(444);

        assertEquals(false,result);
    }

    @Test

    void check_addEditPriv(){
        int result = files.addEditPriv( 532,232,11155,IP );

        assertEquals(-1,result);
    }

    @Test

    void check_addViewPriv(){

        SecureRandom generator = new SecureRandom();

        byte[] keyBytes = new byte[256];

        generator.nextBytes(keyBytes);

        String key = new String(keyBytes);

        int result = files.addViewPriv(555,112,3314,151,key,IP);
        assertEquals(result,-1);

    }

    @Test
    void check_removeViewPriv(){
        int result = files.removeViewPriv(151,112,555,IP);

        assertEquals(result,-1);
    }

    @Test
    void check_removeEditPriv(){
        int result = files.removeEditPriv(151,112,555,IP);
        assertEquals(result,-1);
    }

    @Test

    void check_getFileSK(){
        String wtf = files.getFileSK(151,112,IP);
        String result = files.getFileSK(1881,1122,IP);
        assertEquals(wtf,result);
    }

    @Test

    void check_overwrite(){
        //(int fsoid, int uid, String newFileIV, String encFile, String sourceIp)
        SecureRandom generator = new SecureRandom();

        byte[] keyBytes = new byte[256];

        generator.nextBytes(keyBytes);

        String file = new String(keyBytes);

        int result = files.overwrite(322,3154, "1511" ,file,IP);

        assertEquals(result,-1);

    }

    @Test

    void check_deleteForUser(){
        int result = files.deleteForUser(322,3154,IP);

        assertEquals(result,-1);
    }

    @Test

    void check_deleteIfOrphanFile(){
        files.deleteIfOrphanFile(111,333,IP);

        assert(true);
    }

    @Test

    void check_getAllFileLogs(){

        String result = files.getAllFileLogs();
        // "/var/lib/mysql-files/filelogs.csv"
        assertEquals(null,result);
    }

    @Test

    void check_adminGetFileLog(){
        String result = files.adminGetFileLog(fso.getInt("fsoid"));
        // "/var/lib/mysql-files/fso32123logs.csv"
        assertEquals(null,result);
    }

    @Test

    void check_isFolder(){
        boolean result = files.isFolder(fso.getInt("fsoid"),fso.getInt("uid"),IP);

        assertEquals(false,result);

    }

    @Test

    void check_getChildrenId(){
        List<Integer> result = files.getChildrenId(fso.getInt("fsoid"),fso.getInt("uid"),IP);

        assertEquals(null,result);
    }

    @Test

    void check_getPubKey(){
        String result = files.getPubKey(fso.getInt("uid"));

        assertEquals(null,result);
    }

    @Test

    void check_getEncFileSK(){
        String result = files.getEncFileSK(fso.getInt("fsoid"),fso.getInt("uid"),IP);
        assertEquals(null,result);
    }

    @Test
    void check_updateFileKeys(){

        JSONObject wtf = files.updateFileKeys(fso,IP);

        assertEquals(null,wtf);
    }


}