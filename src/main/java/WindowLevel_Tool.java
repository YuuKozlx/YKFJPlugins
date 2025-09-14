import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;
import ij.Menus;
import ij.gui.Toolbar;
import ij.measure.Calibration;
import ij.plugin.tool.PlugInTool;
import ij.process.ImageStatistics;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;

/**
 * Window/Level Tool with multiple CT preset windows.
 */
public final class WindowLevel_Tool extends PlugInTool implements ActionListener {
    public WindowLevel_Tool() {
        // 插件构造时注册自己到工具栏
        Toolbar.addPlugInTool(this);
    }

    static final int AUTO_THRESHOLD = 5000;

    int autoThreshold;
    private double currentMin = 0;
    private double currentMax = 0;
    private double rescaleIntercept = 0;
    private double rescaleSlope = 1.0;
    private double ctImgLevel, ctImgWidth;
    private int lastX = -1;
    private int lastY = -1;
    private ImagePlus impLast = null;
    private PopupMenu popup1 = null, oldPopup = null;
    private MenuItem autoItem, resetItem;
    private final int OFFSET = 0;
    private boolean RGB, isCT = false;

    // -------------------- CT 窗宽窗位枚举 --------------------
    public enum CtWindowPreset {
        // ===== 大体部位 =====
        BRAIN("Brain", 40, 80),
        LUNG("Lung", -500, 1500),
        ABDOMEN("Abdomen", 56, 340),
        LIVER("Liver", 93, 108),
        SPLEEN("Spleen", 50, 100),
        KIDNEY("Kidney", 40, 400),
        BONE("Bone", 300, 1500),
        SPINE("Spine", 150, 1000),
        MEDIASTINUM("Mediastinum", 40, 350),
        HEART("Heart", 50, 400),

        // ===== 脑部细分 =====
        BRAIN_GRAY("Brain-GrayMatter", 35, 70),
        BRAIN_WHITE("Brain-WhiteMatter", 45, 90),
        BRAIN_CSF("Brain-CSF", 15, 40),

        // ===== 肺部细分 =====
        LUNG_UPPER("Lung-UpperLobe", -450, 1400),
        LUNG_LOWER("Lung-LowerLobe", -520, 1600),
        LUNG_MIDDLE("Lung-MiddleLobe", -480, 1500),

        // ===== 腹部细分 =====
        ABDOMEN_LIVER("Abdomen-Liver", 60, 350),
        ABDOMEN_SPLEEN("Abdomen-Spleen", 50, 100),
        ABDOMEN_KIDNEY("Abdomen-Kidney", 40, 400),
        ABDOMEN_PANCREAS("Abdomen-Pancreas", 55, 300),

        // ===== 骨骼细分 =====
        BONE_VERTEBRA("Bone-Vertebra", 200, 1000),
        BONE_SKULL("Bone-Skull", 600, 2800),
        BONE_RIBS("Bone-Ribs", 500, 2500),

        // ===== 特殊窗位 =====
        MEDIASTINUM_LARGE("Mediastinum-LargeVessels", 40, 400),
        HEART_MYOCARDIUM("Heart-Myocardium", 50, 350);

        public final String name;
        public final double level;
        public final double width;

        CtWindowPreset(String name, double level, double width) {
            this.name = name;
            this.level = level;
            this.width = width;
        }

        public static CtWindowPreset findByName(String name) {
            for (CtWindowPreset preset : values()) {
                if (preset.name.equals(name)) return preset;
            }
            return null;
        }

        public String getName() { return name; }

    }


    // -------------------- 鼠标操作 --------------------
    @Override
    public void mousePressed(ImagePlus imp, MouseEvent e) {
        RGB = imp.getType() == ImagePlus.COLOR_RGB;
        if (impLast != imp) setupImage(imp, false);
        lastX = e.getX();
        lastY = e.getY();
        currentMin = imp.getDisplayRangeMin();
        currentMax = imp.getDisplayRangeMax();
    }

    @Override
    public void mouseDragged(ImagePlus imp, MouseEvent e) {
        double minMaxDifference = currentMax - currentMin;
        int x = e.getX();
        int y = e.getY();
        int xDiff = x - lastX;
        int yDiff = y - lastY;
        int totalWidth = (int) (imp.getWidth() * imp.getCanvas().getMagnification());
        int totalHeight = (int) (imp.getHeight() * imp.getCanvas().getMagnification());
        double xRatio = ((double) xDiff) / totalWidth;
        double yRatio = ((double) yDiff) / totalHeight;

        double xScaledValue = -minMaxDifference * xRatio; // invert x
        double yScaledValue = minMaxDifference * yRatio;

        adjustWindowLevel(imp, xScaledValue, yScaledValue);
    }

    void adjustWindowLevel(ImagePlus imp, double xDifference, double yDifference) {
        double currentWindow = currentMax - currentMin;
        double currentLevel = currentMin + (.5 * currentWindow);

        double newWindow = currentWindow + xDifference;
        double newLevel = currentLevel + yDifference;

        if (newWindow < 0) newWindow = 0;
        if (newLevel < 0) newLevel = 0;

        double printWin = newWindow * rescaleSlope;
        double printLev = (newLevel + getCoef0(imp)) * rescaleSlope + rescaleIntercept;
        IJ.showStatus("Window: " + IJ.d2s(printWin) + ", Level: " + IJ.d2s(printLev));

        double newMin = newLevel - (.5 * newWindow);
        double newMax = newLevel + (.5 * newWindow);

        imp.setDisplayRange(newMin, newMax);
        if (RGB) imp.draw();
        else imp.updateAndDraw();
    }

    // -------------------- 菜单 --------------------
    @Override
    public void showPopupMenu(MouseEvent e, Toolbar par) {
        addPopupMenu(par);
        popup1.show(e.getComponent(), e.getX() + OFFSET, e.getY() + OFFSET);
    }

    void addPopupMenu(Toolbar par) {
        ImagePlus imp = IJ.getImage();
        if (impLast != imp) setupImage(imp, true);
        if (popup1 != null) return;
        par.remove(oldPopup);
        oldPopup = null;
        popup1 = new PopupMenu();
        if (Menus.getFontSize() != 0)
            popup1.setFont(Menus.getFont());

        autoItem = new MenuItem("Auto");
        autoItem.addActionListener(this);
        popup1.add(autoItem);

        resetItem = new MenuItem("Reset");
        resetItem.addActionListener(this);
        popup1.add(resetItem);

        popup1.addSeparator();

        for (CtWindowPreset preset : CtWindowPreset.values()) {
            MenuItem item = new MenuItem(preset.getName());
            item.addActionListener(this);
            popup1.add(item);
        }

        par.add(popup1);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        String cmd = e.getActionCommand();
        if ("Auto".equals(cmd))
            autoItemActionPerformed(e);
        else if ("Reset".equals(cmd))
            resetItemActionPerformed(e);
        else
            maybeSetCt(cmd);
    }

    private void setupImage(ImagePlus imp, boolean fullSetup) {
        if (imp == null) return;
        if (fullSetup) {
            RGB = imp.getType() == ImagePlus.COLOR_RGB;
            currentMin = imp.getDisplayRangeMin();
            currentMax = imp.getDisplayRangeMax();
        }
        boolean currType = isCTImage(imp);
        if (currType != isCT) {
            oldPopup = popup1;
            popup1 = null;
        }
        isCT = currType;
        if (RGB) imp.getProcessor().snapshot();
        autoThreshold = 0;
        impLast = imp;
    }

    private void autoItemActionPerformed(ActionEvent evt) {
        if (impLast == null || !impLast.isVisible()) return;
        int depth = impLast.getBitDepth();
        if (depth != 16 && depth != 32) {
            resetItemActionPerformed(evt);
            return;
        }
        Calibration cal = impLast.getCalibration();
        impLast.setCalibration(null);
        ImageStatistics stat1 = impLast.getStatistics();
        impLast.setCalibration(cal);

        int limit = stat1.pixelCount / 10;
        int[] histogram = stat1.histogram;
        if (autoThreshold < 10) autoThreshold = AUTO_THRESHOLD;
        else autoThreshold /= 2;
        int threshold = stat1.pixelCount / autoThreshold;

        int i = -1;
        boolean found;
        int count;
        do { i++; count = histogram[i]; if(count>limit) count=0; found=count>threshold; } while (!found && i<255);
        int hmin = i;
        i = 256;
        do { i--; count = histogram[i]; if(count>limit) count=0; found=count>threshold; } while (!found && i>0);
        int hmax = i;

        if (hmax >= hmin) {
            currentMin = stat1.histMin + hmin * stat1.binSize;
            currentMax = stat1.histMin + hmax * stat1.binSize;
            if (currentMin == currentMax) {
                currentMin = stat1.min;
                currentMax = stat1.max;
            }
            adjustWindowLevel(impLast, 0, 0);
        }
    }

    private void resetItemActionPerformed(ActionEvent evt) {
        if (impLast == null || !impLast.isVisible()) return;
        impLast.resetDisplayRange();
        currentMin = impLast.getDisplayRangeMin();
        currentMax = impLast.getDisplayRangeMax();
        autoThreshold = 0;
        if (RGB) {
            impLast.getProcessor().reset();
            currentMin = 0;
            currentMax = 255;
            impLast.setDisplayRange(currentMin, currentMax);
            impLast.draw();
        } else
            adjustWindowLevel(impLast, 0, 0);
    }

    private void maybeSetCt(String cmd) {
        if (impLast == null || !impLast.isVisible()) return;
        autoThreshold = 0;

        // 找到对应的窗位枚举
        CtWindowPreset preset = null;
        for (CtWindowPreset p : CtWindowPreset.values()) {
            if (p.getName().equals(cmd)) {
                preset = p;
                break;
            }
        }
        if (preset == null) return;

        // 获取窗位和窗宽
        double level1 = preset.level;
        double width1 = preset.width;

        // 转换为像素空间（考虑 DICOM Slope / Intercept）
        width1 = width1 / rescaleSlope;
        level1 = (level1 - rescaleIntercept) / rescaleSlope;
        level1 -= getCoef0(impLast);

        // 计算最终显示范围
        currentMin = level1 - width1 / 2;
        currentMax = level1 + width1 / 2;

        // 应用到图像
        impLast.setDisplayRange(currentMin, currentMax);
        impLast.updateAndDraw();
    }

    private double getCoef0(ImagePlus img) {
        double[] coef = img.getCalibration().getCoefficients();
        double retVal = 0.;
        if (coef != null) retVal = coef[0];
        return retVal;
    }

    private boolean isCTImage(ImagePlus img) {
        rescaleIntercept = 0;
        rescaleSlope = 1.0;
        ctImgLevel = 56;
        ctImgWidth = 340;
        String meta = img.getStack().getSliceLabel(1);
        if (meta == null || !meta.contains("0010,0010")) meta = (String) img.getProperty("Info");
        if (meta == null) return false;

        String val1 = getDicomValue(meta, "0008,0016");
        if (val1 == null) return false;
        if (val1.startsWith("1.2.840.10008.5.1.4.1.1.20")) return false;
        if (val1.startsWith("1.2.840.10008.5.1.4.1.1.2")) {
            if (getCoef0(img) == 0.) {
                val1 = getDicomValue(meta, "0028,1052");
                rescaleIntercept = parseDouble(val1, 0.);
            }
            val1 = getDicomValue(meta, "0028,1053");
            rescaleSlope = parseDouble(val1, 1.0);
            val1 = getDicomValue(meta, "0028,1050");
            ctImgLevel = parseDouble(val1, ctImgLevel);
            val1 = getDicomValue(meta, "0028,1051");
            ctImgWidth = parseDouble(val1, ctImgWidth);
            String manufacturer = getDicomValue(meta, "0008,1090");
            if ("VARICAM".equals(manufacturer) || "INFINIA".equals(manufacturer) || "QUASAR".equals(manufacturer)) {
                rescaleIntercept = -1000.;
                ctImgLevel += rescaleIntercept;
            }
            return true;
        }
        return false;
    }

    double parseDouble(String tmp1, double defaultValue) {
        try {
            return (tmp1 != null && !tmp1.isEmpty()) ? Double.parseDouble(tmp1) : defaultValue;
        } catch (Exception e) { return defaultValue; }
    }

    private String getDicomValue(String meta, String key1) {
        if (meta == null) return null;
        int k0 = meta.indexOf(key1);
        if (k0 < 0) return null;
        int k1 = meta.indexOf("\n", k0);
        if (k1 < 0) return null;
        String tmp1 = meta.substring(k0, k1);
        k1 = tmp1.indexOf(": ");
        if (k1 > 0) tmp1 = tmp1.substring(k1 + 2);
        String ret1 = tmp1.trim();
        return ret1.isEmpty() ? null : ret1;
    }

    @Override
    public String getToolIcon() { return "T0b12W Tbb12L"; }

    @Override
    public String getToolName() { return "Window Level Tool (right click for Reset, Auto)"; }

    public static void main(String[] args) {
        new ImageJ();
        Toolbar.addPlugInTool(new WindowLevel_Tool());
        Toolbar.getInstance().setTool("Window Level Tool (right click for Reset, Auto)");
        ImagePlus imp = IJ.openImage("http://imagej.net/images/boats.gif");
        imp.show();
    }
}
