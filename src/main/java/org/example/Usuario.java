package org.example;

import com.digitalpersona.onetouch.DPFPTemplate;

public class Usuario {
    private int id;
    private String nombre;
    private DPFPTemplate template; // Huella dactilar

    public Usuario(int id, String nombre, DPFPTemplate template) {
        this.id = id;
        this.nombre = nombre;
        this.template = template;
    }

    public int getId() { return id; }
    public String getNombre() { return nombre; }
    public DPFPTemplate getTemplate() { return template; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    public void setTemplate(DPFPTemplate template) { this.template = template; }

    @Override
    public String toString() {
        return "ID: " + id + " | Usuario: " + nombre;
    }
}
