# Folio 项目规范

## 设计
项目所使用的设计语言为 Material Design
在设计界面时 需遵循 Material 3 技能的规范
路径为 C:\Users\Administrator\.zcode\skills\material-3

## 更新日志

项目包含一个更新日志文件
路径为 C:\Users\Administrator\Desktop
名称为 Folio更新日志.txt

更新日志仅需记录对比上个已发布版本所进行的净更改 但在每次代码修改后都需判断是否记录日志

一个例子：
在 v1.0.0 的开发过程中
1. 加入了搜索功能
2. 修复了搜索功能带来的BUG
3. 微调了搜索功能

在 v1.0.0 的更新日志中 实际只需要在 changelog 中写下第一项即可 因为这些属于未公开功能的内部迭代

更新日志中有五个条目依次为：新增、改进、修复、调整、移除
请确保相关条目被放在了正确的位置

具体 changelog 风格及格式可参考前文

### 图标获取
用户会从 Material Symbols 中提供项目所需的图标
所有图标均会被下载至 C:\Users\Administrator\Downloads

### 杂项
- 禁止私自在真机进行与键盘输入有关的调试 这会导致输入法的BUG 应要求用户进行手动调试
- baseline profile 无法在真机上录制 原因未知 需要使用虚拟机 


