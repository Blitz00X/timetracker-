package com.timetracker.controller;

import com.timetracker.model.Category;
import com.timetracker.util.CategoryColorUtil;
import javafx.geometry.Insets;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.ListCell;
import javafx.scene.control.MenuItem;
import javafx.scene.layout.HBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.text.Text;

/**
 * ListCell implementation for displaying categories with contextual actions.
 */
class CategoryListCell extends ListCell<Category> {

    private final MainController controller;
    private final MenuItem setLimitItem;
    private final MenuItem adjustRemainingItem;
    private final MenuItem resetUsageItem;
    private final MenuItem deleteItem;
    private final ContextMenu contextMenu;

    CategoryListCell(MainController controller) {
        this.controller = controller;
        this.setLimitItem = new MenuItem("Set Daily Limit...");
        this.adjustRemainingItem = new MenuItem("Adjust Today's Remaining...");
        this.resetUsageItem = new MenuItem("Reset Today's Usage");
        this.deleteItem = new MenuItem("Delete Category");
        this.contextMenu = new ContextMenu(setLimitItem, adjustRemainingItem, resetUsageItem, deleteItem);
        wireActions();
    }

    private void wireActions() {
        setLimitItem.setOnAction(event -> {
            Category item = getItem();
            if (item != null) {
                controller.selectCategory(item);
                controller.promptSetCategoryLimit(item);
            }
        });
        adjustRemainingItem.setOnAction(event -> {
            Category item = getItem();
            if (item != null) {
                controller.selectCategory(item);
                controller.promptAdjustRemainingTime(item);
            }
        });
        resetUsageItem.setOnAction(event -> {
            Category item = getItem();
            if (item != null && item.getDailyLimitMinutes() != null) {
                controller.selectCategory(item);
                controller.promptResetCategoryUsage(item);
            }
        });
        deleteItem.setOnAction(event -> {
            Category item = getItem();
            if (item != null) {
                controller.selectCategory(item);
                controller.promptDeleteCategory(item);
            }
        });
    }

    @Override
    protected void updateItem(Category item, boolean empty) {
        super.updateItem(item, empty);
        if (empty || item == null) {
            setText(null);
            setContextMenu(null);
            setGraphic(null);
        } else {
            String colorHex = CategoryColorUtil.colorFor(item.getName());
            Circle dot = new Circle(5, Color.web(colorHex));
            Text name = new Text(controller.formatCategoryDisplay(item));
            name.getStyleClass().add("list-cell-text");
            HBox box = new HBox(8, dot, name);
            box.setPadding(new Insets(2, 0, 2, 0));
            setGraphic(box);
            setText(null);
            resetUsageItem.setDisable(item.getDailyLimitMinutes() == null);
            setContextMenu(contextMenu);
        }
    }
}
