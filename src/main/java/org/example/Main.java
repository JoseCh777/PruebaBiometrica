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

    // ── SDK ───────────────────────────────────────────────────────────────
    private DPFPCapture    lector;
    private DPFPEnrollment enrollment;
    private boolean capturandoEnrollment   = false;
    private boolean capturandoVerificacion = false;

    // ── DAO ───────────────────────────────────────────────────────────────
    private final UsuarioDAO usuarioDAO = new UsuarioDAO();
    private boolean usarOracle = false;

    // ── Datos en memoria ──────────────────────────────────────────────────
    private final List<Usuario> usuarios = new ArrayList<>();
    private int     contadorId    = 1;
    private Usuario usuarioEnEdicion = null;

    // ── UI ────────────────────────────────────────────────────────────────
    private JLabel        lblEstado;
    private JLabel        lblImagen;
    private JLabel        lblMuestras;
    private JLabel        lblModoBD;
    private JList<String> listaUsuarios;
    private DefaultListModel<String> modeloLista;

    // ── Campos del formulario activo (se llenaron en el diálogo) ─────────
    private String pendienteNombre;
    private String pendienteApellido;
    private String pendienteCedula;
    private String pendienteTelefono;

    // ─────────────────────────────────────────────────────────────────────
    public Main() {
        configurarVentana();
        construirUI();
        inicializarSDK();
        verificarConexionOracle();
        cargarDesdeOracle();
    }

    // ── Ventana ───────────────────────────────────────────────────────────
    private void configurarVentana() {
        setTitle("PruebaBiometrica — GYMBROT");
        setSize(980, 640);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        getContentPane().setBackground(BG_DARK);
        setLayout(new BorderLayout());
    }

    // ── Oracle ────────────────────────────────────────────────────────────
    private void verificarConexionOracle() {
        usarOracle = usuarioDAO.probarConexion();
        if (usarOracle) {
            lblModoBD.setText("🟢 Oracle conectado");
            lblModoBD.setForeground(ACCENT2);
        } else {
            lblModoBD.setText("🔴 Sin BD — modo local");
            lblModoBD.setForeground(WARN);
        }
    }

    private void cargarDesdeOracle() {
        if (!usarOracle) return;
        List<Usuario> cargados = usuarioDAO.listarTodos();
        usuarios.addAll(cargados);
        if (!cargados.isEmpty()) {
            contadorId = cargados.stream().mapToInt(Usuario::getId).max().orElse(0) + 1;
        }
        actualizarLista();
        setEstado("Usuarios cargados desde Oracle: " + cargados.size(), TEXT_MUTED);
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
                    SwingUtilities.invokeLater(() -> setEstado("✅ Lector conectado", ACCENT2));
                }
                @Override
                public void readerDisconnected(DPFPReaderStatusEvent e) {
                    SwingUtilities.invokeLater(() -> setEstado("❌ Lector desconectado", DANGER));
                }
            });

            lector.startCapture();
            setEstado("🔵 Lector listo — selecciona una acción", TEXT_MUTED);

        } catch (Exception ex) {
            setEstado("⚠ Error al iniciar lector: " + ex.getMessage(), DANGER);
        }
    }

    // ── Procesamiento de muestra ──────────────────────────────────────────
    private void procesarMuestra(DPFPSample muestra) {
        mostrarImagen(muestra);
        if (capturandoEnrollment)        procesarEnrollment(muestra);
        else if (capturandoVerificacion) procesarVerificacion(muestra);
    }

    // ─────────────────────────────────────────────────────────────────────
    // CREAR — abre diálogo de datos ANTES de capturar huella
    // ─────────────────────────────────────────────────────────────────────
    private void iniciarCrear() {
        usuarioEnEdicion = null;
        boolean ok = mostrarDialogoDatos(null);
        if (!ok) return;

        enrollment = DPFPGlobal.getEnrollmentFactory().createEnrollment();
        capturandoEnrollment   = true;
        capturandoVerificacion = false;
        actualizarIndicadores();
        setEstado("👆 Creando «" + pendienteNombre + " " + pendienteApellido
                + "» — coloca el dedo " + enrollment.getFeaturesNeeded() + " veces...", ACCENT);
    }

    // ─────────────────────────────────────────────────────────────────────
    // EDITAR — abre diálogo prellenado, luego captura nueva huella
    // ─────────────────────────────────────────────────────────────────────
    private void iniciarEditar() {
        int idx = listaUsuarios.getSelectedIndex();
        if (idx < 0) { error("Selecciona un usuario de la lista."); return; }
        usuarioEnEdicion = usuarios.get(idx);

        boolean ok = mostrarDialogoDatos(usuarioEnEdicion);
        if (!ok) return;

        enrollment = DPFPGlobal.getEnrollmentFactory().createEnrollment();
        capturandoEnrollment   = true;
        capturandoVerificacion = false;
        actualizarIndicadores();
        setEstado("✏ Editando «" + usuarioEnEdicion.getNombreCompleto()
                + "» — coloca el nuevo dedo " + enrollment.getFeaturesNeeded() + " veces...", WARN);
    }

    // ─────────────────────────────────────────────────────────────────────
    // DIÁLOGO DE DATOS — formulario con nombre, apellido, CC, teléfono
    // ─────────────────────────────────────────────────────────────────────
    private boolean mostrarDialogoDatos(Usuario prefill) {
        JDialog dlg = new JDialog(this, prefill == null ? "Nuevo Usuario" : "Editar Usuario", true);
        dlg.setSize(420, 360);
        dlg.setLocationRelativeTo(this);
        dlg.setResizable(false);
        dlg.getContentPane().setBackground(BG_DARK);
        dlg.setLayout(new BorderLayout());

        // Título
        JLabel lblTit = new JLabel(prefill == null ? "  Nuevo Usuario" : "  Editar Usuario");
        lblTit.setFont(new Font("Segoe UI", Font.BOLD, 16));
        lblTit.setForeground(ACCENT);
        lblTit.setBackground(BG_PANEL);
        lblTit.setOpaque(true);
        lblTit.setBorder(new EmptyBorder(14, 16, 14, 16));
        dlg.add(lblTit, BorderLayout.NORTH);

        // Formulario
        JPanel form = new JPanel(new GridBagLayout());
        form.setBackground(BG_DARK);
        form.setBorder(new EmptyBorder(20, 24, 10, 24));
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(7, 6, 7, 6);
        g.fill   = GridBagConstraints.HORIZONTAL;

        JTextField fNombre   = campo();
        JTextField fApellido = campo();
        JTextField fCedula   = campo();
        JTextField fTelefono = campo();

        if (prefill != null) {
            fNombre  .setText(prefill.getNombre());
            fApellido.setText(prefill.getApellido());
            fCedula  .setText(prefill.getNumeroCedula());
            fCedula  .setEditable(false); // CC no se puede cambiar
            fTelefono.setText(prefill.getTelefono());
        }

        String[] labels = {"Nombre:", "Apellido:", "N° Cédula:", "Teléfono:"};
        JTextField[] campos = {fNombre, fApellido, fCedula, fTelefono};

        for (int i = 0; i < labels.length; i++) {
            JLabel lbl = new JLabel(labels[i]);
            lbl.setForeground(TEXT_MUTED);
            lbl.setFont(new Font("Segoe UI", Font.PLAIN, 13));
            g.gridx = 0; g.gridy = i; g.weightx = 0;
            form.add(lbl, g);
            g.gridx = 1; g.weightx = 1.0;
            form.add(campos[i], g);
        }

        dlg.add(form, BorderLayout.CENTER);

        // Botones
        JPanel botones = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 12));
        botones.setBackground(BG_DARK);
        JButton btnCancelar = btn("Cancelar", DANGER);
        JButton btnAceptar  = btn("Continuar →", ACCENT2);

        final boolean[] resultado = {false};

        btnCancelar.addActionListener(e -> dlg.dispose());
        btnAceptar.addActionListener(e -> {
            String n = fNombre.getText().trim();
            String a = fApellido.getText().trim();
            String c = fCedula.getText().trim();
            String t = fTelefono.getText().trim();

            if (n.isEmpty() || a.isEmpty() || c.isEmpty() || t.isEmpty()) {
                JOptionPane.showMessageDialog(dlg, "Todos los campos son obligatorios.", "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }

            pendienteNombre   = n;
            pendienteApellido = a;
            pendienteCedula   = c;
            pendienteTelefono = t;
            resultado[0] = true;
            dlg.dispose();
        });

        botones.add(btnCancelar);
        botones.add(btnAceptar);
        dlg.add(botones, BorderLayout.SOUTH);
        dlg.setVisible(true);
        return resultado[0];
    }

    // ── Enrollment ────────────────────────────────────────────────────────
    private void procesarEnrollment(DPFPSample muestra) {
        try {
            DPFPFeatureSet features = extraerFeatures(muestra, DPFPDataPurpose.DATA_PURPOSE_ENROLLMENT);
            if (features == null) { setEstado("⚠ Calidad baja, intenta de nuevo", WARN); return; }

            enrollment.addFeatures(features);
            actualizarIndicadores();

            int faltantes = enrollment.getFeaturesNeeded();
            if (faltantes > 0) {
                setEstado("👆 Coloca el dedo " + faltantes + " vez(ces) más...", ACCENT);
                return;
            }

            // Enrollment completo
            byte[] templateBytes = enrollment.getTemplate().serialize();

            if (usuarioEnEdicion != null) {
                // Actualizar datos
                usuarioEnEdicion.setNombre(pendienteNombre);
                usuarioEnEdicion.setApellido(pendienteApellido);
                usuarioEnEdicion.setTelefono(pendienteTelefono);
                usuarioEnEdicion.setTemplateBytes(templateBytes);

                if (usarOracle) usuarioDAO.actualizar(usuarioEnEdicion);
                setEstado("✅ Usuario actualizado: " + usuarioEnEdicion.getNombreCompleto(), ACCENT2);
                usuarioEnEdicion = null;
            } else {
                // Crear nuevo
                Usuario nuevo = new Usuario(
                        contadorId++,
                        pendienteNombre,
                        pendienteApellido,
                        pendienteCedula,
                        pendienteTelefono,
                        templateBytes
                );
                usuarios.add(nuevo);
                if (usarOracle) usuarioDAO.insertar(nuevo);
                setEstado("✅ Usuario creado: " + nuevo.getNombreCompleto(), ACCENT2);
            }

            capturandoEnrollment = false;
            lblMuestras.setText("");
            actualizarLista();

        } catch (DPFPImageQualityException e) {
            setEstado("⚠ Imagen de baja calidad, intenta de nuevo", WARN);
        } catch (Exception e) {
            setEstado("❌ Error: " + e.getMessage(), DANGER);
            capturandoEnrollment = false;
        }
    }

    // ── Verificación ──────────────────────────────────────────────────────
    private void iniciarVerificacion() {
        if (usuarios.isEmpty()) { error("No hay usuarios registrados."); return; }
        capturandoVerificacion = true;
        capturandoEnrollment   = false;
        setEstado("👆 Coloca el dedo para verificar...", ACCENT);
    }

    private void procesarVerificacion(DPFPSample muestra) {
        try {
            DPFPFeatureSet features = extraerFeatures(muestra, DPFPDataPurpose.DATA_PURPOSE_VERIFICATION);
            if (features == null) { setEstado("⚠ No se pudo leer la huella", WARN); return; }

            DPFPVerification verificador = DPFPGlobal.getVerificationFactory().createVerification();

            for (Usuario u : usuarios) {
                DPFPTemplate t = DPFPGlobal.getTemplateFactory().createTemplate();
                t.deserialize(u.getTemplateBytes());

                DPFPVerificationResult res = verificador.verify(features, t);
                if (res.isVerified()) {
                    setEstado("✅ ¡Bienvenido, " + u.getNombreCompleto()
                            + "! | CC: " + u.getNumeroCedula()
                            + " | FAR: " + res.getFalseAcceptRate(), ACCENT2);
                    capturandoVerificacion = false;
                    resaltarEnLista(u);
                    mostrarDetalleUsuario(u);
                    return;
                }
            }

            setEstado("❌ Huella no reconocida", DANGER);
            capturandoVerificacion = false;

        } catch (Exception e) {
            setEstado("❌ Error: " + e.getMessage(), DANGER);
            capturandoVerificacion = false;
        }
    }

    // ── Eliminar ──────────────────────────────────────────────────────────
    private void eliminarUsuario() {
        int idx = listaUsuarios.getSelectedIndex();
        if (idx < 0) { error("Selecciona un usuario de la lista."); return; }
        Usuario u = usuarios.get(idx);
        int ok = JOptionPane.showConfirmDialog(this,
                "¿Eliminar al usuario «" + u.getNombreCompleto() + "»?\nCC: " + u.getNumeroCedula(),
                "Confirmar eliminación", JOptionPane.YES_NO_OPTION);
        if (ok == JOptionPane.YES_OPTION) {
            if (usarOracle) usuarioDAO.eliminar(u.getNumeroCedula());
            usuarios.remove(idx);
            actualizarLista();
            setEstado("🗑 Usuario eliminado: " + u.getNombreCompleto(), TEXT_MUTED);
        }
    }

    // ── Ventana de detalle (se abre tras verificación exitosa) ────────────
    private void mostrarDetalleUsuario(Usuario u) {
        JDialog dlg = new JDialog(this, "Usuario Identificado", true);
        dlg.setSize(360, 280);
        dlg.setLocationRelativeTo(this);
        dlg.setResizable(false);
        dlg.getContentPane().setBackground(BG_DARK);
        dlg.setLayout(new BorderLayout());

        JLabel lblTit = new JLabel("  ✅ Identidad Verificada");
        lblTit.setFont(new Font("Segoe UI", Font.BOLD, 15));
        lblTit.setForeground(ACCENT2);
        lblTit.setBackground(BG_PANEL);
        lblTit.setOpaque(true);
        lblTit.setBorder(new EmptyBorder(14, 16, 14, 16));
        dlg.add(lblTit, BorderLayout.NORTH);

        JPanel info = new JPanel(new GridLayout(5, 1, 0, 8));
        info.setBackground(BG_CARD);
        info.setBorder(new EmptyBorder(20, 24, 20, 24));

        info.add(fila("Nombre completo:", u.getNombreCompleto()));
        info.add(fila("N° Cédula:",       u.getNumeroCedula()));
        info.add(fila("Teléfono:",         u.getTelefono()));
        info.add(fila("ID interno:",       String.valueOf(u.getId())));
        info.add(fila("Huella:",           u.getTemplateBytes() != null ? "✅ Registrada" : "❌ Sin huella"));

        dlg.add(info, BorderLayout.CENTER);

        JPanel bot = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 12));
        bot.setBackground(BG_DARK);
        JButton btnCerrar = btn("Cerrar", ACCENT);
        btnCerrar.addActionListener(e -> dlg.dispose());
        bot.add(btnCerrar);
        dlg.add(bot, BorderLayout.SOUTH);

        dlg.setVisible(true);
    }

    private JPanel fila(String etiqueta, String valor) {
        JPanel p = new JPanel(new BorderLayout());
        p.setBackground(BG_CARD);
        JLabel lEtiq = new JLabel(etiqueta);
        lEtiq.setForeground(TEXT_MUTED);
        lEtiq.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        JLabel lVal = new JLabel(valor);
        lVal.setForeground(TEXT_MAIN);
        lVal.setFont(new Font("Segoe UI", Font.BOLD, 13));
        p.add(lEtiq, BorderLayout.WEST);
        p.add(lVal,  BorderLayout.EAST);
        return p;
    }

    // ── SDK Helpers ───────────────────────────────────────────────────────
    private DPFPFeatureSet extraerFeatures(DPFPSample muestra, DPFPDataPurpose proposito) {
        try {
            DPFPFeatureExtraction extractor = DPFPGlobal.getFeatureExtractionFactory().createFeatureExtraction();
            return extractor.createFeatureSet(muestra, proposito);
        } catch (DPFPImageQualityException e) { return null; }
    }

    private void mostrarImagen(DPFPSample muestra) {
        try {
            DPFPSampleConversion conv = DPFPGlobal.getSampleConversionFactory();
            Image img    = conv.createImage(muestra);
            Image scaled = img.getScaledInstance(160, 180, Image.SCALE_SMOOTH);
            lblImagen.setIcon(new ImageIcon(scaled));
            lblImagen.setText("");
        } catch (Exception e) { lblImagen.setText("Sin imagen"); }
    }

    private void actualizarIndicadores() {
        if (enrollment == null) return;
        int done = 4 - enrollment.getFeaturesNeeded();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 4; i++) sb.append(i < done ? "🟢 " : "⚪ ");
        lblMuestras.setText(sb.toString());
    }

    // ── UI Helpers ────────────────────────────────────────────────────────
    private void setEstado(String msg, Color color) {
        if (lblEstado == null) return;
        lblEstado.setText(msg); lblEstado.setForeground(color);
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
                listaUsuarios.setSelectedIndex(i); return;
            }
        }
    }

    private JTextField campo() {
        JTextField tf = new JTextField();
        tf.setBackground(BG_PANEL);
        tf.setForeground(TEXT_MAIN);
        tf.setCaretColor(ACCENT);
        tf.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        tf.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(BORDER_C, 1),
                new EmptyBorder(5, 8, 5, 8)));
        return tf;
    }

    private JButton btn(String texto, Color color) {
        JButton b = new JButton(texto);
        b.setFont(new Font("Segoe UI", Font.BOLD, 13));
        b.setForeground(color);
        b.setBackground(BG_CARD);
        b.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(color, 1, true),
                new EmptyBorder(10, 14, 10, 14)));
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

    // ── UI principal ──────────────────────────────────────────────────────
    private void construirUI() {

        // Header
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(BG_PANEL);
        header.setBorder(new EmptyBorder(12, 24, 12, 24));

        JLabel titulo = new JLabel("GYMBROT — Gestión Biométrica");
        titulo.setFont(new Font("Segoe UI", Font.BOLD, 20));
        titulo.setForeground(ACCENT);

        JPanel headerRight = new JPanel(new GridLayout(2, 1, 0, 2));
        headerRight.setBackground(BG_PANEL);
        lblEstado = new JLabel("Iniciando...");
        lblEstado.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        lblEstado.setForeground(TEXT_MUTED);
        lblEstado.setHorizontalAlignment(SwingConstants.RIGHT);

        lblModoBD = new JLabel("Verificando BD...");
        lblModoBD.setFont(new Font("Segoe UI", Font.BOLD, 12));
        lblModoBD.setForeground(TEXT_MUTED);
        lblModoBD.setHorizontalAlignment(SwingConstants.RIGHT);

        headerRight.add(lblEstado);
        headerRight.add(lblModoBD);
        header.add(titulo,      BorderLayout.WEST);
        header.add(headerRight, BorderLayout.EAST);
        add(header, BorderLayout.NORTH);

        // Panel izquierdo — huella
        JPanel panelIzq = new JPanel(new BorderLayout(0, 10));
        panelIzq.setBackground(BG_DARK);
        panelIzq.setBorder(new EmptyBorder(20, 20, 20, 10));
        panelIzq.setPreferredSize(new Dimension(210, 0));

        JLabel lblFT = new JLabel("Vista previa", SwingConstants.CENTER);
        lblFT.setFont(new Font("Segoe UI", Font.BOLD, 12));
        lblFT.setForeground(TEXT_MUTED);

        JPanel cardImg = new JPanel(new BorderLayout());
        cardImg.setBackground(BG_CARD);
        cardImg.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(BORDER_C, 1, true), new EmptyBorder(10, 10, 10, 10)));

        lblImagen = new JLabel("Sin huella", SwingConstants.CENTER);
        lblImagen.setFont(new Font("Segoe UI", Font.ITALIC, 13));
        lblImagen.setForeground(TEXT_MUTED);
        lblImagen.setPreferredSize(new Dimension(160, 180));

        lblMuestras = new JLabel("", SwingConstants.CENTER);
        lblMuestras.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 16));
        lblMuestras.setBorder(new EmptyBorder(8, 0, 0, 0));

        cardImg.add(lblImagen,   BorderLayout.CENTER);
        cardImg.add(lblMuestras, BorderLayout.SOUTH);
        panelIzq.add(lblFT,   BorderLayout.NORTH);
        panelIzq.add(cardImg, BorderLayout.CENTER);
        add(panelIzq, BorderLayout.WEST);

        // Centro — lista
        JPanel centro = new JPanel(new BorderLayout(0, 10));
        centro.setBackground(BG_DARK);
        centro.setBorder(new EmptyBorder(20, 10, 20, 10));

        JLabel lblListaTit = new JLabel("Usuarios registrados");
        lblListaTit.setForeground(TEXT_MUTED);
        lblListaTit.setFont(new Font("Segoe UI", Font.BOLD, 12));
        lblListaTit.setBorder(new EmptyBorder(0, 0, 6, 0));

        modeloLista   = new DefaultListModel<>();
        listaUsuarios = new JList<>(modeloLista);
        listaUsuarios.setBackground(BG_CARD);
        listaUsuarios.setForeground(TEXT_MAIN);
        listaUsuarios.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        listaUsuarios.setSelectionBackground(new Color(60, 80, 120));
        listaUsuarios.setSelectionForeground(Color.WHITE);
        listaUsuarios.setFixedCellHeight(36);
        listaUsuarios.setBorder(new EmptyBorder(4, 8, 4, 8));

        // Doble clic en lista → ver detalle
        listaUsuarios.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    int idx = listaUsuarios.getSelectedIndex();
                    if (idx >= 0) mostrarDetalleUsuario(usuarios.get(idx));
                }
            }
        });

        JScrollPane scroll = new JScrollPane(listaUsuarios);
        scroll.setBorder(new LineBorder(BORDER_C, 1, true));
        scroll.getViewport().setBackground(BG_CARD);

        JPanel listaWrap = new JPanel(new BorderLayout(0, 6));
        listaWrap.setBackground(BG_DARK);
        listaWrap.add(lblListaTit, BorderLayout.NORTH);
        listaWrap.add(scroll,      BorderLayout.CENTER);
        centro.add(listaWrap, BorderLayout.CENTER);
        add(centro, BorderLayout.CENTER);

        // Derecha — botones
        JPanel derecha = new JPanel(new GridLayout(5, 1, 0, 10));
        derecha.setBackground(BG_DARK);
        derecha.setBorder(new EmptyBorder(20, 10, 20, 20));
        derecha.setPreferredSize(new Dimension(165, 0));

        JButton btnCrear     = btn("➕  Crear",     ACCENT2);
        JButton btnVerificar = btn("🔍  Verificar", ACCENT);
        JButton btnEditar    = btn("✏  Editar",    WARN);
        JButton btnEliminar  = btn("🗑  Eliminar",  DANGER);
        JButton btnDetalle   = btn("👁  Detalle",   new Color(180, 140, 255));

        btnCrear    .addActionListener(e -> iniciarCrear());
        btnVerificar.addActionListener(e -> iniciarVerificacion());
        btnEditar   .addActionListener(e -> iniciarEditar());
        btnEliminar .addActionListener(e -> eliminarUsuario());
        btnDetalle  .addActionListener(e -> {
            int idx = listaUsuarios.getSelectedIndex();
            if (idx >= 0) mostrarDetalleUsuario(usuarios.get(idx));
            else error("Selecciona un usuario de la lista.");
        });

        derecha.add(btnCrear);
        derecha.add(btnVerificar);
        derecha.add(btnEditar);
        derecha.add(btnEliminar);
        derecha.add(btnDetalle);
        add(derecha, BorderLayout.EAST);

        addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) {
                if (lector != null) try { lector.stopCapture(); } catch (Exception ignored) {}
            }
        });
    }

    public static void main(String[] args) {
        try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); }
        catch (Exception ignored) {}
        SwingUtilities.invokeLater(() -> new Main().setVisible(true));
    }
}
