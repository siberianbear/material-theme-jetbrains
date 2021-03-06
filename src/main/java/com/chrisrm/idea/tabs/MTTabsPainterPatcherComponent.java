/*
 *  The MIT License (MIT)
 *
 *  Copyright (c) 2018 Chris Magnussen and Elior Boukhobza
 *
 *  Permission is hereby granted, free of charge, to any person obtaining a copy
 *  of this software and associated documentation files (the "Software"), to deal
 *  in the Software without restriction, including without limitation the rights
 *  to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *  copies of the Software, and to permit persons to whom the Software is
 *  furnished to do so, subject to the following conditions:
 *
 *  The above copyright notice and this permission notice shall be included in all
 *  copies or substantial portions of the Software.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 *  SOFTWARE.
 *
 */

package com.chrisrm.idea.tabs;

import com.chrisrm.idea.MTConfig;
import com.chrisrm.idea.MTThemeManager;
import com.chrisrm.idea.config.ConfigNotifier;
import com.chrisrm.idea.themes.MTThemeable;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManagerEvent;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.wm.impl.ToolWindowImpl;
import com.intellij.ui.ColorUtil;
import com.intellij.ui.tabs.JBTabsPosition;
import com.intellij.ui.tabs.TabInfo;
import com.intellij.ui.tabs.impl.DefaultEditorTabsPainter;
import com.intellij.ui.tabs.impl.JBEditorTabs;
import com.intellij.ui.tabs.impl.JBEditorTabsPainter;
import com.intellij.ui.tabs.impl.ShapeTransform;
import com.intellij.util.ReflectionUtil;
import com.intellij.util.messages.MessageBus;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.ui.UIUtil;
import javassist.*;
import javassist.expr.ExprEditor;
import javassist.expr.FieldAccess;
import javassist.expr.MethodCall;
import net.sf.cglib.proxy.Enhancer;
import net.sf.cglib.proxy.MethodInterceptor;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.lang.reflect.Field;

/**
 * Patch the Tabs Component to get the Material Design style
 *
 * @author Dennis.Ushakov
 */
public final class MTTabsPainterPatcherComponent implements ApplicationComponent {

  public static final String TABS_HEIGHT = "MTTabsHeight";
  public static final String BOLD_TABS = "MTBoldTabs";
  //  public static final String WINDOW_HEADER_HACK = "MTWindowHeaderHack";

  private final MTThemeable theme;
  private final MTConfig config;

  public MTTabsPainterPatcherComponent() {
    config = MTConfig.getInstance();
    theme = config.getSelectedTheme().getTheme();

    PropertiesComponent.getInstance().setValue(TABS_HEIGHT, 25, 24);
    PropertiesComponent.getInstance().setValue(BOLD_TABS, false, false);
    //    PropertiesComponent.getInstance().setValue(WINDOW_HEADER_HACK, false);

  }

  /**
   * Hack ToolWindowHeight to not take TabsUtil.getHeight
   */
  private static void hackToolWindowHeader() {
    // Hack method
    try {
      final ClassPool cp = new ClassPool(true);
      cp.insertClassPath(new ClassClassPath(ToolWindowImpl.class));
      final CtClass ctClass = cp.get("com.intellij.openapi.wm.impl.ToolWindowHeader");
      final CtMethod ctMethod = ctClass.getDeclaredMethod("getPreferredSize");
      ctMethod.instrument(new ExprEditor() {
        @Override
        public void edit(final MethodCall m) throws CannotCompileException {
          if (m.getClassName().equals("com.intellij.ui.tabs.TabsUtil") && m.getMethodName().equals("getTabsHeight")) {
            m.replace("{ $_ = com.intellij.util.ui.JBUI.scale(25); }");
          }
        }
      });

      ctClass.toClass();

      final CtClass ctClass1 = cp.get("com.intellij.ui.tabs.impl.JBEditorTabs");
      final CtMethod useBoldLabels = ctClass1.getDeclaredMethod("useBoldLabels");
      useBoldLabels.instrument(new ExprEditor() {
        @Override
        public void edit(final FieldAccess f) throws CannotCompileException {
          if (f.getFieldName().equals("isMac")) {
            f.replace("{ $_ = true; }");
          }
        }

        @Override
        public void edit(final MethodCall m) throws CannotCompileException {
          if (m.getMethodName().equals("is")) {
            final String code = String.format("com.intellij.ide.util.PropertiesComponent.getInstance().getBoolean(\"%s\", false)",
                BOLD_TABS);
            m.replace(String.format("{ $_ = %s; }", code));
          }
        }
      });

      ctClass1.toClass();

      final CtClass ctClass2 = cp.get("com.intellij.openapi.wm.impl.InternalDecorator");
      final CtMethod ctMethod2 = ctClass2.getDeclaredMethod("init");
      ctMethod2.instrument(new ExprEditor() {
        @Override
        public void edit(final MethodCall m) throws CannotCompileException {
          if (m.getMethodName().equals("setBackground")) {
            m.replace("{ $1 = com.intellij.util.ui.UIUtil.getPanelBackground(); $proceed($$); }");
          }
        }
      });

      ctClass2.toClass();
    } catch (final Exception e) {
      e.printStackTrace();
    }
  }

  private static void hackSpeedSearch() {
    // Hack method
    try {
      final ClassPool cp = new ClassPool(true);
      cp.insertClassPath(new ClassClassPath(ToolWindowImpl.class));
      final CtClass ctClass = cp.get("com.intellij.ui.SpeedSearchBase$SearchPopup");
      final CtConstructor declaredConstructor = ctClass.getDeclaredConstructors()[0];
      declaredConstructor.instrument(new ExprEditor() {
        @Override
        public void edit(final MethodCall m) throws CannotCompileException {
          if (m.getMethodName().equals("setBackground")) {
            final String bgColor = "com.intellij.util.ui.UIUtil.getToolTipBackground().brighter();";
            m.replace(String.format("{ $1 = %s; $proceed($$); }", bgColor));
          } else if (m.getMethodName().equals("setBorder")) {
            final String borderColor = "null";
            m.replace(String.format("{ $1 = %s; $proceed($$); }", borderColor));
          }
        }
      });

      ctClass.toClass();
    } catch (final Exception e) {
      e.printStackTrace();
    }
  }

  /**
   * Hack TabsUtil,getHeight to override SDK
   */
  private void hackTabsGetHeight() throws
      NotFoundException,
      CannotCompileException {

    final ClassPool cp = new ClassPool(true);
    cp.insertClassPath(new ClassClassPath(TabInfo.class));
    final CtClass ctClass = cp.get("com.intellij.ui.tabs.impl.TabLabel");
    final CtMethod ctMethod = ctClass.getDeclaredMethod("getPreferredSize");

    ctMethod.instrument(new ExprEditor() {
      @Override
      public void edit(final MethodCall m) throws CannotCompileException {
        if (m.getClassName().equals("com.intellij.ui.tabs.TabsUtil") && m.getMethodName().equals("getTabsHeight")) {
          final String code = String.format("com.intellij.ide.util.PropertiesComponent.getInstance().getInt(\"%s\", 25)", TABS_HEIGHT);
          final String isDebugTab = "myInfo.getTabActionPlace() != null ? myInfo.getTabActionPlace().contains(\"debugger\") : true";
          m.replace(String.format("{ $_ = com.intellij.util.ui.JBUI.scale(%s); }", code));
        }
      }
    });
    ctClass.toClass();

    // Hack JBRunnerTabs
    final CtClass tabLabelClass = cp.get("com.intellij.execution.ui.layout.impl.JBRunnerTabs$MyTabLabel");
    final CtMethod ctMethod2 = tabLabelClass.getDeclaredMethod("getPreferredSize");

    ctMethod2.instrument(new ExprEditor() {
      @Override
      public void edit(final FieldAccess f) throws CannotCompileException {
        if (f.getFieldName().equals("height") && f.isReader()) {
          f.replace("{ $_ = com.intellij.util.ui.JBUI.scale(25); }");
        }
      }
    });
    tabLabelClass.toClass();
  }

  @Override
  public void disposeComponent() {

  }

  @NotNull
  @Override
  public String getComponentName() {
    return "MTTabsPainterPatcherComponent";
  }

  @Override
  public void initComponent() {
    final MessageBus bus = ApplicationManagerEx.getApplicationEx().getMessageBus();

    final MessageBusConnection connect = bus.connect();
    connect.subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, new FileEditorManagerListener() {
      @Override
      public void selectionChanged(@NotNull final FileEditorManagerEvent event) {
        final FileEditor editor = event.getNewEditor();
        if (editor != null) {
          Component component = editor.getComponent();
          while (component != null) {
            if (component instanceof JBEditorTabs) {
              patchPainter((JBEditorTabs) component);
              return;
            }
            component = component.getParent();
          }
        }
      }
    });

    // Listen to option save to set tab height
    setTabsHeight();
    connect.subscribe(ConfigNotifier.CONFIG_TOPIC, mtConfig -> setTabsHeight());

    try {
      hackTabsGetHeight();
      hackToolWindowHeader();
      hackSpeedSearch();
    } catch (final Exception e) {
      e.printStackTrace();
    }
  }

  /**
   * Patch tabsPainter
   *
   * @param component
   */
  private void patchPainter(final JBEditorTabs component) {
    final JBEditorTabsPainter painter = ReflectionUtil.getField(JBEditorTabs.class, component, JBEditorTabsPainter.class, "myDarkPainter");

    if (painter instanceof MTTabsPainter) {
      return;
    }

    final MTTabsPainter tabsPainter = new MTTabsPainter(component);
    final JBEditorTabsPainter proxy = (MTTabsPainter) Enhancer.create(MTTabsPainter.class, (MethodInterceptor) (o, method, objects,
                                                                                                                methodProxy) -> {
      final Object result = method.invoke(tabsPainter, objects);
      final Color defaultColor = theme.getBorderColor();

      // Custom props
      final boolean isColorEnabled = config.isHighlightColorEnabled();
      final Color borderColor = isColorEnabled ? config.getHighlightColor() : defaultColor;
      final int borderThickness = config.getHighlightThickness();

      if ("paintSelectionAndBorder".equals(method.getName())) {
        paintSelectionAndBorder(objects, borderColor, borderThickness, tabsPainter);
      }

      return result;
    });

    ReflectionUtil.setField(JBEditorTabs.class, component, JBEditorTabsPainter.class, "myDefaultPainter", proxy);
    ReflectionUtil.setField(JBEditorTabs.class, component, JBEditorTabsPainter.class, "myDarkPainter", proxy);
  }

  /**
   * Paint tab selected and highlight border
   *
   * @param objects
   * @param borderColor
   * @param borderThickness
   * @param tabsPainter
   */
  private void paintSelectionAndBorder(final Object[] objects,
                                       final Color borderColor,
                                       final int borderThickness,
                                       final MTTabsPainter tabsPainter)
      throws ClassNotFoundException, NoSuchFieldException, IllegalAccessException {
    // Get the shapeinfo class because it is protected
    final Class<?> clazz = Class.forName("com.intellij.ui.tabs.impl.JBTabsImpl$ShapeInfo");

    // Retrieve arguments
    final Graphics2D g2d = (Graphics2D) objects[0];
    final Rectangle rect = (Rectangle) objects[1];
    final Object selectedShape = objects[2];
    final Insets insets = (Insets) objects[3];
    final Color tabColor = (Color) objects[4];

    // Retrieve private fields of ShapeInfo class
    final Field pathField = clazz.getField("path");
    final Field fillPathField = clazz.getField("fillPath");
    final Field labelPathField = clazz.getField("labelPath");

    final ShapeTransform path = (ShapeTransform) pathField.get(selectedShape);
    final ShapeTransform fillPath = (ShapeTransform) fillPathField.get(selectedShape);
    final ShapeTransform labelPath = (ShapeTransform) labelPathField.get(selectedShape);

    // Other properties needed for drawing
    final Insets i = path.transformInsets(insets);
    final int rectX = rect.x;
    final int rectY = rect.y;
    final int rectHeight = rect.height;

    // The tabs component
    final JBEditorTabs tabsComponent = tabsPainter.getTabsComponent();

    // Position of tabs
    final JBTabsPosition position = tabsComponent.getTabsPosition();

    // color me
    tabsPainter.fillSelectionAndBorder(g2d, fillPath, tabColor, rectX, rectY, rectHeight);

    // paint the bottom bar in non darcula lafs
    if (!UIUtil.isUnderDarcula()) {
      final Color lineColor = tabsPainter.getContrastColor();
      g2d.setColor(lineColor);
      g2d.fillRect(i.left, labelPath.getMaxY() - 5, path.getMaxX(), 5);
    }

    // Finally paint the active tab highlighter
    g2d.setColor(borderColor);

    if (position == JBTabsPosition.bottom) {
      // Paint on top
      g2d.fillRect(rect.x, rect.y - 1, rect.width, borderThickness);
    } else if (position == JBTabsPosition.top) {
      // Paint on bottom
      g2d.fillRect(rect.x, rect.y + rect.height - borderThickness + 1, rect.width, borderThickness);
      g2d.setColor(UIUtil.CONTRAST_BORDER_COLOR);
      g2d.drawLine(Math.max(0, rect.x - 1), rect.y, rect.x + rect.width, rect.y);
    } else if (position == JBTabsPosition.left) {
      g2d.fillRect(rect.x, rect.y, borderThickness, rect.height);
    } else if (position == JBTabsPosition.right) {
      g2d.fillRect(rect.x + rect.width - borderThickness + 1, rect.y, borderThickness, rect.height);
    }
  }

  private void setTabsHeight() {
    MTThemeManager.getInstance().setTabsHeight();
  }

  public static class MTTabsPainter extends DefaultEditorTabsPainter {
    public MTTabsPainter() {
      super(null);
    }

    public MTTabsPainter(final JBEditorTabs tabs) {
      super(tabs);
    }

    public final void fillSelectionAndBorder(final Graphics2D g,
                                             final ShapeTransform selectedShape,
                                             final Color tabColor,
                                             final int x,
                                             final int y,
                                             final int height) {
      g.setColor(tabColor != null ? tabColor : getDefaultTabColor());
      g.fill(selectedShape.getShape());
    }

    @Override
    public final Color getBackgroundColor() {
      final MTConfig config = MTConfig.getInstance();
      final MTThemeable mtTheme = config.getSelectedTheme().getTheme();
      return mtTheme.getBackgroundColor();
    }

    public final Color getContrastColor() {
      final MTConfig config = MTConfig.getInstance();
      final MTThemeable mtTheme = config.getSelectedTheme().getTheme();
      return config.getIsContrastMode() ? mtTheme.getContrastColor() : mtTheme.getBackgroundColor();
    }

    public final JBEditorTabs getTabsComponent() {
      return myTabs;
    }

    @Override
    protected final Color getDefaultTabColor() {
      if (myDefaultTabColor != null) {
        return myDefaultTabColor;
      }

      return getBackgroundColor();
    }

    @Override
    protected final Color getInactiveMaskColor() {
      final float opacity = (float) (MTConfig.getInstance().getTabOpacity() / 100.0);
      return ColorUtil.withAlpha(getContrastColor(), opacity);
    }
  }
}

