import com.android.ide.common.vectordrawable.Svg2Vector;
import com.android.ide.common.vectordrawable.VdPreview;
import java.nio.file.*;
import java.io.*;
import javax.imageio.ImageIO;
public class CobaltVectorSync {
  public static void main(String[] args) throws Exception {
    Path input = Path.of(args[1]), output = Path.of(args[2]);
    Files.createDirectories(output);
    try (var paths = Files.list(input)) {
      for (Path file : paths.sorted().toList()) {
        if (args[0].equals("convert") && file.toString().endsWith(".svg")) {
          String name = file.getFileName().toString().replace(".svg", ".xml");
          try (var stream = Files.newOutputStream(output.resolve(name))) {
            String warnings = Svg2Vector.parseSvgToXml(file, stream);
            if (warnings != null && !warnings.isBlank()) throw new IOException(file + ": " + warnings);
          }
        } else if (args[0].equals("render") && file.toString().endsWith(".xml")) {
          StringBuilder errors = new StringBuilder();
          var img = VdPreview.getPreviewFromVectorXml(VdPreview.TargetSize.createFromMaxDimension(192), Files.readString(file), errors);
          if (img == null || !errors.isEmpty()) throw new IOException(file + ": " + errors);
          ImageIO.write(img, "PNG", output.resolve(file.getFileName().toString().replace(".xml", ".png")).toFile());
        }
      }
    }
  }
}
