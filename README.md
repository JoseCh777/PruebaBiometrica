# PruebaBiometrica — GYMBROT

Proyecto de prueba para el lector **HID U.are.U 4500** con el SDK DigitalPersona 1.6.1 en Java 24.

## Funcionalidades
- ➕ **Crear** usuario con nombre + huella dactilar
- 🔍 **Verificar** huella contra todos los usuarios registrados
- ✏ **Editar** nombre y huella de un usuario existente
- 🗑 **Eliminar** usuario de la lista

---

## Requisitos previos

### 1. Driver del lector — Non-WBF (obligatorio)
El lector **debe** tener instalado el driver **Non-WBF** (no el WBF de Windows Hello).

- Descarga: **HID DigitalPersona 4500 Non-WBF Driver**
  - Archivo: `4500-Legacy-driver-4.1.0.217_WithInstaller.zip`
  - Desde: https://www.hidglobal.com/drivers
- Instala el `setup-x64.msi` como administrador
- El lector aparecerá en el Administrador de dispositivos bajo **"Authentication Device"** (no "Biometric")

> ⚠ Si tienes el driver WBF instalado, el SDK Java mostrará "The fingerprint reader was disconnected".
> Desinstálalo con: `pnputil /delete-driver oem4.inf /uninstall /force` (CMD como administrador)

### 2. SDK DigitalPersona 1.6.1
Ya instalado en: `C:\Program Files\DigitalPersona\`

### 3. Java JDK 24
Ubicación: `C:\Program Files\Java\jdk-24`

---

## Configuración en IntelliJ IDEA

### Paso 1 — Abrir el proyecto
`File → Open` → selecciona la carpeta del proyecto

### Paso 2 — Agregar los 4 JARs del SDK
1. Clic derecho en el proyecto → **Open Module Settings** (`F4`)
2. Ve a **Dependencies** → clic en **+** → **JARs or directories**
3. Navega a: `C:\Program Files\DigitalPersona\Bin\Java\`
4. Selecciona estos 4 archivos:
   - `dpotjni.jar`
   - `dpotapi.jar`
   - `dpfpenrollment.jar`
   - `dpfpverification.jar`
5. Clic **OK → Apply → OK**

### Paso 3 — Configurar VM Options (importante para Java 24)
1. `Run → Edit Configurations`
2. En **VM options** agrega:
```
--enable-native-access=ALL-UNNAMED
```
Esto elimina los warnings de acceso nativo que genera Java 24 con el SDK.

### Paso 4 — Configurar JAVA_HOME (si ejecutas desde CMD)
```
set JAVA_HOME=C:\Program Files\Java\jdk-24
```

---

## Clases del proyecto

| Clase | Descripción |
|---|---|
| `Main.java` | Interfaz Swing + lógica CRUD + integración SDK |
| `Usuario.java` | Modelo: id, nombre, DPFPTemplate (huella) |

---

## Métodos correctos del SDK v1.6.1

> Referencia para evitar errores comunes:

| Lo que parece lógico | Método real en v1.6.1 |
|---|---|
| `result.isAchieved()` | `result.isVerified()` |
| `getSampleConversionFactory().createSampleConversion()` | `getSampleConversionFactory()` (ya devuelve el objeto directo) |
| `conv.convertToBitmap(muestra)` | `conv.createImage(muestra)` |
| `DPFPImageConversion` | `DPFPSampleConversion` |

---

## Uso de la aplicación

1. **Crear usuario:** Escribe el nombre → clic "➕ Crear" → coloca el dedo **4 veces** (verás los indicadores 🟢)
2. **Verificar:** Clic "🔍 Verificar" → coloca el dedo → muestra quién es
3. **Editar:** Selecciona usuario en la lista → clic "✏ Editar" → escribe el nuevo nombre → coloca el nuevo dedo 4 veces
4. **Eliminar:** Selecciona usuario en la lista → clic "🗑 Eliminar" → confirma

---

## Notas importantes

- Los datos se guardan **en memoria** (se pierden al cerrar la app). Para persistencia se necesita BD.
- El lector debe estar conectado **antes** de ejecutar la aplicación.
- Los warnings de SonarQube y Java 24 (`transient or serializable`, `unnamed pattern`) no afectan el funcionamiento.
- El lector aparece bajo **"Authentication Device"** en el Administrador de dispositivos, no bajo "Biometric" — esto es normal con el driver Non-WBF.

---

## Próximo paso — Integración con GYMBROT
Una vez validado el funcionamiento del lector, el siguiente paso es integrar el SDK en el proyecto GYMBROT con:
- Persistencia en base de datos (guardar template como `byte[]`)
- Capa `HuellaService.java` en el módulo `service/`
- Control de acceso biométrico en la capa `controller/`
