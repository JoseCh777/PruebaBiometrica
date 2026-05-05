package org.example;

public class Usuario {
    private int id;
    private String nombre;
    private byte[] templateBytes;

    public Usuario(int id, String nombre, byte[] templateBytes) {
        this.id = id;
        this.nombre = nombre;
        this.templateBytes = templateBytes;
    }

    public int getId() { return id; }
    public String getNombre() { return nombre; }
    public byte[] getTemplateBytes() { return templateBytes; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    public void setTemplateBytes(byte[] templateBytes) { this.templateBytes = templateBytes; }

    @Override
    public String toString() { return "ID: " + id + " | Usuario: " + nombre; }
}