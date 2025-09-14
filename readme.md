# 使用方法

构建后使用命令```mvn clean package```将会在```target```目录下生成```YuuKo_CT_Plugins-x.x.x.jar```，将其放置于
```<Fiji-Install-Dir>/plugins```这个目录下，打开Fiji就可使用
两个插件
- ***WindowLevelTool*** : 改造了已有的Window_Level_Tool插件，去除了isCT的判断，并新增了几个预设（常用的WW/WL）
- ***SaveWithHeader*** : 对数据处理以后经常需要保存原始数据的Header信息，但Fiji读取时需要skip Header，找回这段信息比较麻烦，
可能需要第三方语言再次处理。这个插件可用于辅助，但是需要知道Header的字节大小