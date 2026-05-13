package org.example;

import java.io.Serializable;

public class Usuario implements Serializable {
    private static final long serialVersionUID = 2L;

    private int    id;
    private String nombre;
    private String apellido;
    private String numeroCedula;
    private String telefono;
    private byte[] templateBytes;

    public Usuario(int id, String nombre, String apellido,
                   String numeroCedula, String telefono, byte[] templateBytes) {
        this.id           = id;
        this.nombre       = nombre;
        this.apellido     = apellido;
        this.numeroCedula = numeroCedula;
        this.telefono     = telefono;
        this.templateBytes = templateBytes;
    }

    public int    getId()           { return id; }
    public String getNombre()       { return nombre; }
    public String getApellido()     { return apellido; }
    public String getNumeroCedula() { return numeroCedula; }
    public String getTelefono()     { return telefono; }
    public byte[] getTemplateBytes(){ return templateBytes; }

    public void setNombre(String nombre)             { this.nombre = nombre; }
    public void setApellido(String apellido)         { this.apellido = apellido; }
    public void setNumeroCedula(String numeroCedula) { this.numeroCedula = numeroCedula; }
    public void setTelefono(String telefono)         { this.telefono = telefono; }
    public void setTemplateBytes(byte[] templateBytes){ this.templateBytes = templateBytes; }

    public String getNombreCompleto() { return nombre + " " + apellido; }

    @Override
    public String toString() {
        return "CC: " + numeroCedula + " | " + nombre + " " + apellido + " | Tel: " + telefono;
    }
}
