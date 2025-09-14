import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;
import ij.gui.GenericDialog;
import ij.gui.Toolbar;
import ij.plugin.tool.PlugInTool;
import ij.io.FileInfo;
import ij.io.ImageWriter;

import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.io.OutputStream;

/**
 * Save image with header using ImageJ internal ImageWriter
 * Supports: 16-bit signed/unsigned, 32-bit signed/unsigned, 32-bit float, 64-bit float
 */
public final class SaveWithHeader_ extends PlugInTool implements ActionListener {

    private ImagePlus impLast = null;
    private PopupMenu popup1 = null, oldPopup = null;

    private static final String[] types = {
            "16-bit signed", "16-bit unsigned",
            "32-bit signed", "32-bit unsigned",
            "32-bit float", "64-bit float"
    };
    public SaveWithHeader_() {
        // 插件构造时注册自己到工具栏
        Toolbar.addPlugInTool(this);
    }


    @Override
    public void showPopupMenu(MouseEvent e, Toolbar par) {
        addPopupMenu(par);
        int OFFSET = 0;
        popup1.show(e.getComponent(), e.getX() + OFFSET, e.getY() + OFFSET);
    }

    private void addPopupMenu(Toolbar par) {
        ImagePlus imp = IJ.getImage();
        if (impLast != imp) impLast = imp;
        if (popup1 != null) return;

        par.remove(oldPopup);
        oldPopup = null;
        popup1 = new PopupMenu();
        MenuItem saveItem = new MenuItem("Save with Header");
        saveItem.addActionListener(this);
        popup1.add(saveItem);
        par.add(popup1);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if ("Save with Header".equals(e.getActionCommand())) {
            saveWithHeaderAction();
        }
    }

    private void saveWithHeaderAction() {
        if (impLast == null || !impLast.isVisible()) {
            IJ.showMessage("Error", "No image available!");
            return;
        }

        try {
            // 1. 输入参数
            GenericDialog gd = new GenericDialog("Save with Header Options");
            gd.addNumericField("Header length (bytes):", 1024, 0);
            gd.addChoice("Image Data Type:", types, "16-bit signed");
            gd.showDialog();
            if (gd.wasCanceled()) return;

            int headerLength = (int) gd.getNextNumber();
            String typeChoice = gd.getNextChoice();

            // 2. header 文件
            ij.io.OpenDialog od = new ij.io.OpenDialog("Choose Source Header File");
            String headerPath = od.getPath();
            if (headerPath == null) return;

            // 3. 目标文件
            ij.io.SaveDialog sd = new ij.io.SaveDialog("Choose Target File", "output.raw", ".raw");
            String dir = sd.getDirectory();
            String file = sd.getFileName();
            if (dir == null || file == null) return;
            String tgtPath = dir + file;

            // 4. 检查路径是否相同
            java.io.File srcFile = new java.io.File(headerPath);
            java.io.File dstFile = new java.io.File(tgtPath);
            if (srcFile.getAbsolutePath().equals(dstFile.getAbsolutePath())) {
                ij.IJ.showMessage("Error", "Source and target file paths cannot be the same!");
                return;
            }

            // 5. 读取 header（只读前 headerLength 字节）
            byte[] headerBytes = new byte[headerLength];
            try (InputStream in = Files.newInputStream(Paths.get(headerPath))) {
                int read = in.read(headerBytes, 0, headerLength);
                if (read < headerLength) {
                    IJ.showMessage("Error", "Header file is smaller than header length!");
                    return;
                }
            }

            // 5. 构建 FileInfo
            FileInfo fi = new FileInfo();
            fi.width = impLast.getWidth();
            fi.height = impLast.getHeight();
            fi.nImages = impLast.getStackSize();
            fi.intelByteOrder = true;
            fi.fileName = tgtPath;
            fi.directory = "";
            switch (typeChoice) {
                case "16-bit signed": fi.fileType = FileInfo.GRAY16_SIGNED; break;
                case "16-bit unsigned": fi.fileType = FileInfo.GRAY16_UNSIGNED; break;
                case "32-bit signed": fi.fileType = FileInfo.GRAY32_INT; break;
                case "32-bit unsigned": fi.fileType = FileInfo.GRAY32_UNSIGNED; break;
                case "32-bit float": fi.fileType = FileInfo.GRAY32_FLOAT; break;
                case "64-bit float": fi.fileType = FileInfo.GRAY64_FLOAT; break;
                default: IJ.showMessage("Error", "Unsupported type"); return;
            }

            // 6. 获取像素数组
            Object[] stack = new Object[fi.nImages];
            for (int s = 0; s < fi.nImages; s++) {
                stack[s] = impLast.getStack().getPixels(s + 1);
            }
            fi.pixels = stack;

            // 7. 启动后台线程
            javax.swing.SwingWorker<Void, Integer> worker = new javax.swing.SwingWorker<Void, Integer>() {
                @Override
                protected Void doInBackground() throws Exception {
                    try (OutputStream out = Files.newOutputStream(Paths.get(tgtPath))) {
                        out.write(headerBytes, 0, headerLength);

                        ImageWriter writer = new ImageWriter(fi);

                        if (fi.fileType == FileInfo.GRAY16_SIGNED) {
                            int n = fi.width * fi.height;
                            short[][] tempStack = new short[fi.nImages][];
                            for (int s = 0; s < fi.nImages; s++) {
                                short[] original = (short[]) stack[s];
                                short[] temp = new short[n];
                                for (int i = 0; i < n; i++) temp[i] = (short)(original[i] - 32768);
                                tempStack[s] = temp;
                                publish(s + 1); // 更新进度
                            }
                            fi.pixels = tempStack;
                            writer.write(out);
                            fi.pixels = stack;
                        } else {
                            writer.write(out);
                        }
                    }
                    return null;
                }

                @Override
                protected void process(java.util.List<Integer> chunks) {
                    int last = chunks.get(chunks.size() - 1);
                    IJ.showStatus("Saving slice " + last + "/" + fi.nImages);
                    IJ.showProgress(last, fi.nImages);
                }

                @Override
                protected void done() {
                    try {
                        get(); // 捕获异常
                        IJ.showStatus("Save completed");
                        IJ.showMessage("Save", "Image saved successfully!");
                    } catch (Exception ex) {
                        IJ.showMessage("Error", "Save failed: " + ex.getMessage());
                    }
                }
            };
            worker.execute();

        } catch (Exception ex) {
            IJ.showMessage("Error", "Failed: " + ex.getMessage());
        }
    }


    @Override
    public String getToolIcon() { return "T0b12S Tbb12H"; }

    @Override
    public String getToolName() { return "Save Image"; }

    /** Main 测试 */
    public static void main(String[] args) {
        new ImageJ();
        Toolbar.addPlugInTool(new SaveWithHeader_());
        Toolbar.getInstance().setTool("Save with Header");
    }
}
