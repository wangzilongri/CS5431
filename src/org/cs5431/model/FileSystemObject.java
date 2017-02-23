package org.cs5431.model;

public abstract class FileSystemObject {
    private int id;
    private FileSystemObject parentFolder;
    private int ownerId;
    private int editorIds[];
    private int viewerIds[];
    private String name;
    private int size;
}
