package me.coley.recaf.ui.pane.assembler;

import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.stage.WindowEvent;
import me.coley.recaf.RecafUI;
import me.coley.recaf.assemble.ast.Unit;
import me.coley.recaf.code.*;
import me.coley.recaf.config.Configs;
import me.coley.recaf.ui.behavior.Cleanable;
import me.coley.recaf.ui.behavior.MemberEditor;
import me.coley.recaf.ui.behavior.WindowCloseListener;
import me.coley.recaf.ui.behavior.SaveResult;
import me.coley.recaf.ui.control.ClassBorderPane;
import me.coley.recaf.ui.control.ClassStackPane;
import me.coley.recaf.ui.control.ErrorDisplay;
import me.coley.recaf.ui.control.SearchBar;
import me.coley.recaf.ui.control.code.ProblemIndicatorInitializer;
import me.coley.recaf.ui.control.code.ProblemTracking;
import me.coley.recaf.ui.control.code.bytecode.AssemblerArea;
import me.coley.recaf.ui.pane.DockingRootPane;
import me.coley.recaf.ui.pane.DockingWrapperPane;
import me.coley.recaf.ui.util.Icons;
import org.fxmisc.flowless.VirtualizedScrollPane;

/**
 * Wrapper pane of all the assembler components.
 *
 * @author Matt Coley
 * @see AssemblerArea Assembler text editor
 */
public class AssemblerPane extends BorderPane implements MemberEditor, Cleanable, WindowCloseListener {
	private final AssemblerArea assemblerArea;
	private final Tab tab;
	private boolean ignoreNextDisassemble;
	private MemberInfo targetMember;
	private ClassInfo classInfo;

	/**
	 * Setup the assembler pane and it's sub-components.
	 */
	public AssemblerPane() {
		ProblemTracking tracking = new ProblemTracking();
		tracking.setIndicatorInitializer(new ProblemIndicatorInitializer(tracking));
		assemblerArea = new AssemblerArea(tracking);
		Node virtualScroll = new VirtualizedScrollPane<>(assemblerArea);
		Node errorDisplay = new ErrorDisplay(assemblerArea, tracking);
		BorderPane layoutWrapper = new ClassBorderPane(this);
		layoutWrapper.setCenter(virtualScroll);
		ClassStackPane stack = new ClassStackPane(this);
		StackPane.setAlignment(errorDisplay, Configs.editor().errorIndicatorPos);
		StackPane.setMargin(errorDisplay, new Insets(16, 25, 25, 53));
		stack.getChildren().add(layoutWrapper);
		stack.getChildren().add(errorDisplay);

		DockingWrapperPane dockingWrapper = DockingWrapperPane.builder()
				.title("Assembler")
				.content(stack)
				.build();
		tab = dockingWrapper.getTab();
		setCenter(dockingWrapper);
		// TODO: Bottom tabs
		//  - local variable table
		//  - stack analysis

		// Keybinds and other doodads
		Configs.keybinds().installEditorKeys(layoutWrapper);
		SearchBar.install(layoutWrapper, assemblerArea);
	}

	@Override
	public SaveResult save() {
		// Because the 'save' method updates the workspace we must pre-emptively set this flag,
		// even if we cannot be sure if it will succeed.
		ignoreNextDisassemble = true;
		return assemblerArea.save();
	}

	@Override
	public void onUpdate(CommonClassInfo newValue) {
		if (newValue instanceof ClassInfo) {
			classInfo = (ClassInfo) newValue;
			assemblerArea.onUpdate(classInfo);
			// Update target member
			Unit unit = assemblerArea.getLastUnit();
			if (unit != null) {
				String name = unit.getDefinition().getName();
				String desc = unit.getDefinition().getDesc();
				if (unit.isField()) {
					for (FieldInfo field : newValue.getFields()) {
						if (field.getName().equals(name) && field.getDescriptor().equals(desc)) {
							setTargetMember(field);
							break;
						}
					}
				} else {
					for (MethodInfo method : newValue.getMethods()) {
						if (method.getName().equals(name) && method.getDescriptor().equals(desc)) {
							setTargetMember(method);
							break;
						}
					}
				}
			}
			// Skip if we triggered this update
			if (ignoreNextDisassemble) {
				ignoreNextDisassemble = false;
				return;
			}
			// Update disassembly text
			assemblerArea.disassemble();
		}
	}

	@Override
	public void cleanup() {
		assemblerArea.cleanup();
	}

	@Override
	public MemberInfo getTargetMember() {
		return targetMember;
	}

	@Override
	public void setTargetMember(MemberInfo targetMember) {
		this.targetMember = targetMember;
		assemblerArea.setTargetMember(targetMember);
		// Update tab display
		tab.setText(targetMember.getName());
		if (targetMember.isMethod())
			tab.setGraphic(Icons.getMethodIcon((MethodInfo) targetMember));
		else if (targetMember.isField())
			tab.setGraphic(Icons.getFieldIcon((FieldInfo) targetMember));
	}

	@Override
	public CommonClassInfo getCurrentClassInfo() {
		return classInfo;
	}

	@Override
	public boolean supportsEditing() {
		return true;
	}

	@Override
	public Node getNodeRepresentation() {
		return this;
	}

	@Override
	public boolean supportsMemberSelection() {
		return false;
	}

	@Override
	public boolean isMemberSelectionReady() {
		return false;
	}

	@Override
	public void selectMember(MemberInfo memberInfo) {
		// no-op, represents an actual member so nothing to select
	}

	@Override
	public void onClose(WindowEvent e) {
		// The docking system listens to tab close events for managing internal state.
		// But window closing bypasses this, so we need to forward the close request.
		TabPane tabPane = tab.getTabPane();
		if (tabPane != null)
			tabPane.getTabs().remove(tab);
	}

	private static DockingRootPane docking() {
		return RecafUI.getWindows().getMainWindow().getDockingRootPane();
	}
}
