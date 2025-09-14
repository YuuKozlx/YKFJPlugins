package Tools;

import ij.ImageJ;

public class PluginTest {

    public static void main(String[] args) {
        // 启动 ImageJ GUI
        new ImageJ();

        // 创建插件实例
        SaveWithHeader_ tool = new SaveWithHeader_();
        WindowLevel_Tool toolWindow = new WindowLevel_Tool();

        // 添加到工具栏
        ij.gui.Toolbar.addPlugInTool(tool);
        ij.gui.Toolbar.addPlugInTool(toolWindow);
        ij.gui.Toolbar.getInstance().setTool("Save With Header");

    }
}
