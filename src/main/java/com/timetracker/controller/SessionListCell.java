package com.timetracker.controller;

import com.timetracker.model.SessionViewModel;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.ListCell;
import javafx.scene.control.MenuItem;
import javafx.scene.layout.HBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.text.Text;
import com.timetracker.util.CategoryColorUtil;

/**
 * ListCell implementation for displaying sessions with contextual actions.
 */
class SessionListCell extends ListCell<SessionViewModel> {

    private final MainController controller;
    private final MenuItem editItem;
    private final MenuItem deleteItem;
    private final ContextMenu contextMenu;

    SessionListCell(MainController controller) {
        this.controller = controller;
        this.editItem = new MenuItem("Edit Session...");
        this.deleteItem = new MenuItem("Delete Session");
        this.contextMenu = new ContextMenu(editItem, deleteItem);
        wireActions();
    }

    private void wireActions() {
        editItem.setOnAction(event -> {
            SessionViewModel item = getItem();
            if (item != null) {
                controller.promptEditSession(item);
            }
        });
        deleteItem.setOnAction(event -> {
            SessionViewModel item = getItem();
            if (item != null) {
                controller.promptDeleteSession(item);
            }
        });
    }

    @Override
    protected void updateItem(SessionViewModel item, boolean empty) {
        super.updateItem(item, empty);
        if (empty || item == null) {
            setText(null);
            setGraphic(null);
            setContextMenu(null);
        } else {
            String colorHex = CategoryColorUtil.colorFor(item.categoryName());
            Circle dot = new Circle(5, Color.web(colorHex));
            Text text = new Text(item.asDisplayString());
            text.getStyleClass().add("list-cell-text");
            HBox box = new HBox(8, dot, text);
            setGraphic(box);
            setText(null);
            setContextMenu(contextMenu);
        }
    }
}
