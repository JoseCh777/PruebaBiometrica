package org.example;

import com.digitalpersona.onetouch.*;
import com.digitalpersona.onetouch.capture.*;
import com.digitalpersona.onetouch.capture.event.*;
import com.digitalpersona.onetouch.processing.*;
import com.digitalpersona.onetouch.verification.*;

import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.*;
import java.io.ObjectOutputStream;
import java.io.ObjectInputStream;
import java.util.ArrayList;
import java.util.List;

public class Main extends JFrame {

    // ── Colores ───────────────────────────────────────────────────────────
    private static final Color BG_DARK   = new Color(18, 18, 24);
    private static final Color BG_PANEL  = new Color(28, 28, 38);
    private static final Color BG_CARD   = new Color(38, 38, 52);
    private static final Color ACCENT    = new Color(99, 179, 237);
    private static final Color ACCENT2   = new Color(72, 199, 142);
    private static final Color DANGER    = new Color(252, 92, 101);
    private static final Color WARN      = new Color(250, 177, 60);
    private static final Color TEXT_MAIN = new Color(230, 230, 240);
    private static final Color TEXT_MUTED= new Color(130, 130, 150);
    private static final Color BORDER_C  = new Color(55, 55, 75);

    // ── Archivo persistencia ──────────────────────────────────────────────
    private static final String ARCHIVO_DATOS = "usuarios.dat";

    // ── SDK ───────────────────────────────────────────────────────────────
    private DPFPCapture    lector;
    private DPFPEnrollment enrollment;
    private boolean capturandoEnrollment   = false;
    private boolean capturandoVerificacion = false;

    // ── Datos en memoria ──────────────────────────────────────────────────
    private final List<Usuario> usuarios = new ArrayList<>();
    private int     contadorId = 1;
    private Usuario usuarioEnEdicion = null;

    // ── UI ────────────────────────────────────────────────────────────────
    private JLabel             lblEstado;
    private JLabel             lblImagen;
    private JLabel             lblMuestras;
    private JTextField         txtNombre;
    private JList<String>      listaUsuarios;
    private DefaultListModel<String> modeloLista;

    // ─────────────────────────────────────────────────────────────────────
    public Main() {
        configurarVentana();
        construirUI();
        inicializarSDK();
        cargarUsuarios();
    }

    // ── Ventana ───────────────────────────────────────────────────────────
    private void configurarVentana() {
        setTitle("PruebaBiometrica — GYMBROT");
        setSize(900, 620);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        getContentPane().setBackground(BG_DARK);
        setLayout(new BorderLayout());
    }

    // ── SDK ───────────────────────────────────────────────────────────────
    private void inicializarSDK() {
        try {
            lector = DPFPGlobal.getCaptureFactory().createCapture();

            lector.addDataListener(new DPFPDataAdapter() {
                @Override
                public void dataAcquired(DPFPDataEvent e) {
                    SwingUtilities.invokeLater(() -> procesarMuestra(e.getSample()));
                }
            });

            lector.addReaderStatusListener(new DPFPReaderStatusAdapter() {
                @Override
                public void readerConnected(DPFPReaderStatusEvent e) {
                    SwingUtilities.invokeLater(() -> setEstado("Lector conectado", ACCENT2));
                }
                @Override
                public void readerDisconnected(DPFPReaderStatusEvent e) {
                    SwingUtilities.invokeLater(() -> setEstado("Lector desconectado", DANGER));
                }
            });

            lector.startCapture();
            setEstado("Lector listo — selecciona una acción", TEXT_MUTED);

        } catch (Exception ex) {
            setEstado("Error al iniciar lector: " + ex.getMessage(), DANGER);
        }
    }

    // ── Persistencia ──────────────────────────────────────────────────────
    private void guardarUsuarios() {
        try (ObjectOutputStream oos = new ObjectOutputStream(
                new java.io.FileOutputStream(ARCHIVO_DATOS))) {
            oos.writeObject(contadorId);
            oos.writeObject(new ArrayList<>(usuarios));
        } catch (Exception e) {
            setEstado("Error al guardar datos: " + e.getMessage(), DANGER);
        }
    }

    @SuppressWarnings("unchecked")
    private void cargarUsuarios() {
        java.io.File f = new java.io.File(ARCHIVO_DATOS);
        if (!f.exists()) return;
        try (ObjectInputStream ois = new ObjectInputStream(
                new java.io.FileInputStream(f))) {
            contadorId = (int) ois.readObject();
            List<Usuario> cargados = (List<Usuario>) ois.readObject();
            usuarios.addAll(cargados);
            actualizarLista();
            setEstado("Usuarios cargados: " + usuarios.size(), TEXT_MUTED);
        } catch (Exception e) {
            setEstado("Error al cargar datos: " + e.getMessage(), DANGER);
        }
    }

    // ── Procesamiento de muestra ──────────────────────────────────────────
    private void procesarMuestra(DPFPSample muestra) {
        mostrarImagen(muestra);
        if (capturandoEnrollment)        procesarEnrollment(muestra);
        else if (capturandoVerificacion) procesarVerificacion(muestra);
    }

    // ── CREAR / EDITAR — Enrollment ───────────────────────────────────────
    private void iniciarEnrollment() {
        String nombre = txtNombre.getText().trim();
        if (nombre.isEmpty()) {
            error("Ingresa un nombre de usuario primero.");
            return;
        }
        enrollment = DPFPGlobal.getEnrollmentFactory().createEnrollment();
        capturandoEnrollment   = true;
        capturandoVerificacion = false;
        actualizarIndicadores();
        String accion = (usuarioEnEdicion != null)
                ? "Editando «" + usuarioEnEdicion.getNombre() + "»"
                : "Creando usuario";
        setEstado(" " + accion + " — coloca el dedo "
                + enrollment.getFeaturesNeeded() + " veces...", ACCENT);
    }

    private void procesarEnrollment(DPFPSample muestra) {
        try {
            DPFPFeatureSet features = extraerFeatures(muestra, DPFPDataPurpose.DATA_PURPOSE_ENROLLMENT);
            if (features == null) {
                setEstado("[!] Calidad baja, intenta de nuevo", WARN);
                return;
            }

            enrollment.addFeatures(features);
            actualizarIndicadores();

            int faltantes = enrollment.getFeaturesNeeded();
            if (faltantes > 0) {
                setEstado("Coloca el dedo " + faltantes + " vez(ces) más...", ACCENT);
                return;
            }

            // ── Enrollment completo ──
            byte[] templateBytes = enrollment.getTemplate().serialize();
            String nombre = txtNombre.getText().trim();

            if (usuarioEnEdicion != null) {
                usuarioEnEdicion.setNombre(nombre);
                usuarioEnEdicion.setTemplateBytes(templateBytes);
                setEstado("Usuario actualizado: " + nombre, ACCENT2);
                usuarioEnEdicion = null;
            } else {
                usuarios.add(new Usuario(contadorId++, nombre, templateBytes));
                setEstado("Usuario creado: " + nombre, ACCENT2);
            }

            capturandoEnrollment = false;
            txtNombre.setText("");
            lblMuestras.setText("");
            guardarUsuarios();
            actualizarLista();

        } catch (DPFPImageQualityException e) {
            setEstado("Imagen de baja calidad, intenta de nuevo", WARN);
        } catch (Exception e) {
            setEstado("Error en enrollment: " + e.getMessage(), DANGER);
            capturandoEnrollment = false;
        }
    }

    // ── VERIFICAR ─────────────────────────────────────────────────────────
    private void iniciarVerificacion() {
        if (usuarios.isEmpty()) { error("No hay usuarios registrados."); return; }
        capturandoVerificacion = true;
        capturandoEnrollment   = false;
        setEstado(" Coloca el dedo para verificar...", ACCENT);
    }

    private void procesarVerificacion(DPFPSample muestra) {
        try {
            DPFPFeatureSet features = extraerFeatures(muestra, DPFPDataPurpose.DATA_PURPOSE_VERIFICATION);
            if (features == null) {
                setEstado("No se pudo leer la huella, intenta de nuevo", WARN);
                return;
            }

            DPFPVerification verificador =
                    DPFPGlobal.getVerificationFactory().createVerification();

            for (Usuario u : usuarios) {
                DPFPTemplate t = DPFPGlobal.getTemplateFactory().createTemplate();
                t.deserialize(u.getTemplateBytes());

                DPFPVerificationResult res = verificador.verify(features, t);
                if (res.isVerified()) {
                    setEstado("¡Bienvenido, " + u.getNombre()
                            + "!  FAR: " + res.getFalseAcceptRate(), ACCENT2);
                    capturandoVerificacion = false;
                    resaltarEnLista(u);
                    return;
                }
            }

            setEstado("Huella no reconocida", DANGER);
            capturandoVerificacion = false;

        } catch (Exception e) {
            setEstado("Error en verificación: " + e.getMessage(), DANGER);
            capturandoVerificacion = false;
        }
    }

    // ── EDITAR ────────────────────────────────────────────────────────────
    private void editarUsuario() {
        int idx = listaUsuarios.getSelectedIndex();
        if (idx < 0) { error("Selecciona un usuario de la lista."); return; }
        usuarioEnEdicion = usuarios.get(idx);
        txtNombre.setText(usuarioEnEdicion.getNombre());
        iniciarEnrollment();
    }

    // ── ELIMINAR ──────────────────────────────────────────────────────────
    private void eliminarUsuario() {
        int idx = listaUsuarios.getSelectedIndex();
        if (idx < 0) { error("Selecciona un usuario de la lista."); return; }
        Usuario u = usuarios.get(idx);
        int ok = JOptionPane.showConfirmDialog(this,
                "¿Eliminar al usuario «" + u.getNombre() + "»?",
                "Confirmar", JOptionPane.YES_NO_OPTION);
        if (ok == JOptionPane.YES_OPTION) {
            usuarios.remove(idx);
            guardarUsuarios();
            actualizarLista();
            setEstado("[X] Usuario eliminado: " + u.getNombre(), TEXT_MUTED);
        }
    }

    // ── Helpers SDK ───────────────────────────────────────────────────────
    private DPFPFeatureSet extraerFeatures(DPFPSample muestra, DPFPDataPurpose proposito) {
        try {
            DPFPFeatureExtraction extractor =
                    DPFPGlobal.getFeatureExtractionFactory().createFeatureExtraction();
            return extractor.createFeatureSet(muestra, proposito);
        } catch (DPFPImageQualityException e) {
            return null;
        }
    }

    private void mostrarImagen(DPFPSample muestra) {
        try {
            DPFPSampleConversion conv = DPFPGlobal.getSampleConversionFactory();
            Image img = conv.createImage(muestra);
            Image scaled = img.getScaledInstance(160, 180, Image.SCALE_SMOOTH);
            lblImagen.setIcon(new ImageIcon(scaled));
            lblImagen.setText("");
        } catch (Exception e) {
            lblImagen.setText("Sin imagen");
        }
    }

    private void actualizarIndicadores() {
        if (enrollment == null) return;
        int completadas = 4 - enrollment.getFeaturesNeeded();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 4; i++) sb.append(i < completadas ? "[OK] " : "[--] ");
        lblMuestras.setText(sb.toString());
    }

    // ── Helpers UI ────────────────────────────────────────────────────────
    private void setEstado(String msg, Color color) {
        if (lblEstado == null) return;
        lblEstado.setText(msg);
        lblEstado.setForeground(color);
    }

    private void error(String msg) {
        JOptionPane.showMessageDialog(this, msg, "Error", JOptionPane.ERROR_MESSAGE);
    }

    private void actualizarLista() {
        modeloLista.clear();
        for (Usuario u : usuarios) modeloLista.addElement(u.toString());
    }

    private void resaltarEnLista(Usuario u) {
        for (int i = 0; i < usuarios.size(); i++) {
            if (usuarios.get(i).getId() == u.getId()) {
                listaUsuarios.setSelectedIndex(i);
                return;
            }
        }
    }

    // ── Construcción de la UI ─────────────────────────────────────────────
    private void construirUI() {

        // ── Header ──
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(BG_PANEL);
        header.setBorder(new EmptyBorder(14, 24, 14, 24));

        JLabel titulo = new JLabel("GYMBROT — Gestión Biométrica");
        titulo.setFont(new Font("Segoe UI", Font.BOLD, 20));
        titulo.setForeground(ACCENT);

        lblEstado = new JLabel("Iniciando...");
        lblEstado.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        lblEstado.setForeground(TEXT_MUTED);

        header.add(titulo,    BorderLayout.WEST);
        header.add(lblEstado, BorderLayout.EAST);
        add(header, BorderLayout.NORTH);

        // ── Panel izquierdo — vista previa ──
        JPanel panelIzq = new JPanel(new BorderLayout(0, 10));
        panelIzq.setBackground(BG_DARK);
        panelIzq.setBorder(new EmptyBorder(20, 20, 20, 10));
        panelIzq.setPreferredSize(new Dimension(210, 0));

        JLabel lblFingerTitle = new JLabel("Vista previa", SwingConstants.CENTER);
        lblFingerTitle.setFont(new Font("Segoe UI", Font.BOLD, 12));
        lblFingerTitle.setForeground(TEXT_MUTED);

        JPanel cardImg = new JPanel(new BorderLayout());
        cardImg.setBackground(BG_CARD);
        cardImg.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(BORDER_C, 1, true),
                new EmptyBorder(10, 10, 10, 10)));

        lblImagen = new JLabel("Sin huella", SwingConstants.CENTER);
        lblImagen.setFont(new Font("Segoe UI", Font.ITALIC, 13));
        lblImagen.setForeground(TEXT_MUTED);
        lblImagen.setPreferredSize(new Dimension(160, 180));

        lblMuestras = new JLabel("", SwingConstants.CENTER);
        lblMuestras.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        lblMuestras.setBorder(new EmptyBorder(8, 0, 0, 0));

        cardImg.add(lblImagen,   BorderLayout.CENTER);
        cardImg.add(lblMuestras, BorderLayout.SOUTH);

        panelIzq.add(lblFingerTitle, BorderLayout.NORTH);
        panelIzq.add(cardImg,        BorderLayout.CENTER);
        add(panelIzq, BorderLayout.WEST);

        // ── Panel central ──
        JPanel centro = new JPanel(new BorderLayout(0, 14));
        centro.setBackground(BG_DARK);
        centro.setBorder(new EmptyBorder(20, 10, 20, 10));

        // Formulario nombre
        JPanel form = new JPanel(new GridBagLayout());
        form.setBackground(BG_CARD);
        form.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(BORDER_C, 1, true),
                new EmptyBorder(14, 14, 14, 14)));

        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(4, 4, 4, 4);
        g.fill   = GridBagConstraints.HORIZONTAL;

        JLabel lbl = new JLabel("Nombre:");
        lbl.setForeground(TEXT_MUTED);
        lbl.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        g.gridx = 0; g.gridy = 0; g.weightx = 0;
        form.add(lbl, g);

        txtNombre = new JTextField();
        txtNombre.setBackground(BG_PANEL);
        txtNombre.setForeground(TEXT_MAIN);
        txtNombre.setCaretColor(ACCENT);
        txtNombre.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        txtNombre.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(BORDER_C, 1),
                new EmptyBorder(6, 10, 6, 10)));
        g.gridx = 1; g.weightx = 1.0;
        form.add(txtNombre, g);

        centro.add(form, BorderLayout.NORTH);

        // Lista
        modeloLista   = new DefaultListModel<>();
        listaUsuarios = new JList<>(modeloLista);
        listaUsuarios.setBackground(BG_CARD);
        listaUsuarios.setForeground(TEXT_MAIN);
        listaUsuarios.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        listaUsuarios.setSelectionBackground(new Color(60, 80, 120));
        listaUsuarios.setSelectionForeground(Color.WHITE);
        listaUsuarios.setFixedCellHeight(36);
        listaUsuarios.setBorder(new EmptyBorder(4, 8, 4, 8));

        JScrollPane scroll = new JScrollPane(listaUsuarios);
        scroll.setBorder(new LineBorder(BORDER_C, 1, true));
        scroll.getViewport().setBackground(BG_CARD);

        JLabel lblListaTit = new JLabel("Usuarios registrados");
        lblListaTit.setForeground(TEXT_MUTED);
        lblListaTit.setFont(new Font("Segoe UI", Font.BOLD, 12));
        lblListaTit.setBorder(new EmptyBorder(0, 0, 6, 0));

        JPanel listaWrap = new JPanel(new BorderLayout(0, 6));
        listaWrap.setBackground(BG_DARK);
        listaWrap.add(lblListaTit, BorderLayout.NORTH);
        listaWrap.add(scroll,      BorderLayout.CENTER);
        centro.add(listaWrap, BorderLayout.CENTER);
        add(centro, BorderLayout.CENTER);

        // ── Panel derecho — Botones ──
        JPanel derecha = new JPanel(new GridLayout(4, 1, 0, 12));
        derecha.setBackground(BG_DARK);
        derecha.setBorder(new EmptyBorder(20, 10, 20, 20));
        derecha.setPreferredSize(new Dimension(155, 0));

        JButton btnCrear     = btn("  Crear",     ACCENT2);
        JButton btnVerificar = btn("  Verificar", ACCENT);
        JButton btnEditar    = btn("  Editar",    WARN);
        JButton btnEliminar  = btn("  Eliminar",  DANGER);

        btnCrear    .addActionListener(e -> { usuarioEnEdicion = null; iniciarEnrollment(); });
        btnVerificar.addActionListener(e -> iniciarVerificacion());
        btnEditar   .addActionListener(e -> editarUsuario());
        btnEliminar .addActionListener(e -> eliminarUsuario());

        derecha.add(btnCrear);
        derecha.add(btnVerificar);
        derecha.add(btnEditar);
        derecha.add(btnEliminar);
        add(derecha, BorderLayout.EAST);

        addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) {
                if (lector != null) try { lector.stopCapture(); } catch (Exception ignored) {}
            }
        });
    }

    private JButton btn(String texto, Color color) {
        JButton b = new JButton(texto);
        b.setFont(new Font("Segoe UI", Font.BOLD, 13));
        b.setForeground(color);
        b.setBackground(BG_CARD);
        b.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(color, 1, true),
                new EmptyBorder(10, 12, 10, 12)));
        b.setFocusPainted(false);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) {
                b.setBackground(new Color(color.getRed(), color.getGreen(), color.getBlue(), 40));
            }
            @Override public void mouseExited(MouseEvent e) { b.setBackground(BG_CARD); }
        });
        return b;
    }

    // ── Entry point ───────────────────────────────────────────────────────
    public static void main(String[] args) {
        try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); }
        catch (Exception ignored) {}
        SwingUtilities.invokeLater(() -> new Main().setVisible(true));
    }
}