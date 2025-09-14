

import ij.ImageJ;
import ij.gui.Toolbar;

public class PluginTest {

    public static void main(String[] args) {
        // 启动 ImageJ GUI
        new ImageJ();

        // 创建插件实例
        SaveWithHeader_ saveTool = new SaveWithHeader_();
        WindowLevel_Tool wlTool = new WindowLevel_Tool();

        // 添加到工具栏
        Toolbar.addPlugInTool(saveTool);
        //Toolbar.addPlugInTool(wlTool);

        // 设置不同图标，确保工具栏显示两个独立按钮
        Toolbar.getInstance().setTool(saveTool.getToolName()); // 激活 SaveWithHeader_
        // 不用立即设置 wlTool，否则它会覆盖当前激活工具

    }
}
