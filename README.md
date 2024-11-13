# Copy as Markdown 

## Description
This is a simple tool to copy the content of a file to the clipboard in Markdown format.
The result after copying is as follows:

```java

# Project Name: copy2md

## File: src/main/java/bf/com/copy2md/action/CopyFileAsMarkdownAction.java

```java
public class CopyFileAsMarkdownAction extends AnAction {
    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.EDT;
    }
    private final MarkdownFormatter formatter = new MarkdownFormatter();

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        @NotNull Project project = e.getRequiredData(CommonDataKeys.PROJECT);

        VirtualFile[] files = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY);
        if (files == null || files.length == 0) return;

        StringBuilder markdown = new StringBuilder();
        markdown.append("Project Name: ").append(project.getName()).append("\n\n");
        for (VirtualFile file : files) {
            try {
                String content = new String(file.contentsToByteArray(), StandardCharsets.UTF_8);
                markdown.append(formatter.formatFileContent(project, file, content));
            } catch (IOException ioException) {
                ioException.printStackTrace();
            }
        }

        CopyUtil.copyToClipboardWithNotification(markdown.toString(), project);
    }

}
```


## Usage
### Copy selected file(s) as Markdown to clipboard
![img.png](doc/img.png)
### Copy selected code as Markdown to clipboard
![img.png](doc/img_1.png)
### Copy All Opened Tabs as Markdown
![img.png](doc/img_2.png)