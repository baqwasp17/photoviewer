import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.util.HashMap;
import java.util.List;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.Scene;
import javafx.scene.SnapshotParameters;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.CustomMenuItem;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.Slider;
import javafx.scene.effect.ColorAdjust;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.WritableImage;
import javafx.scene.input.DragEvent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.paint.Color;
import javafx.scene.transform.Rotate;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javax.imageio.ImageIO;

public class PhotoViewer extends Application {

	private final static Logger LOGGER = 
		Logger.getLogger(PhotoViewer.class.getName());

	protected ImageView currentViewImage;
	protected Rotate rotate = new Rotate();

	protected ColorAdjust colorAdjust = new ColorAdjust();

	protected Map<String, Slider> sliderLookupMap = new HashMap<>();

	protected ImageViewButtons buttonPanel;

	protected ExecutorService executorService =
		Executors.newSingleThreadScheduledExecutor();

	@Override
	public void start(Stage stage) {

		stage.setTitle("Photo Viewer");
		BorderPane root = new BorderPane();
		Scene scene = new Scene(root, 551, 400, Color.BLACK);
		scene.getStylesheets()
			.add(getClass()
				.getClassLoader()
				.getResource("photo-viewer.css")
				.toExternalForm());

		stage.setScene(scene);

		// Anchor Pane
		AnchorPane mainContentPane = new AnchorPane();

		// Group is a container to hold image view
		Group imageGroup = new Group();
		AnchorPane.setTopAnchor(imageGroup, 0.0);
		AnchorPane.setLeftAnchor(imageGroup, 0.0);

		// Current image view
		currentViewImage = createImageView(rotate);
		imageGroup.getChildren().add(currentViewImage);

		// Custom ButtonPanel (Next, Previous)
		List<ImageInfo> IMAGE_FILES = new ArrayList<>();
		buttonPanel = new ImageViewButtons(IMAGE_FILES);

		// Create a progress indicator
		ProgressIndicator progressIndicator = createProgressIndicator();

		// layer items. Items that are last are on the top
		mainContentPane.getChildren().addAll(imageGroup,
			buttonPanel, progressIndicator);

		// Create menus File, Rotate, Color adjust menus
		Menu fileMenu = createFileMenu(stage, progressIndicator);
		Menu rotateMenu = createRotateMenu();
		Menu colorAdjustMenu = createColorAdjustMenu();
		MenuBar menuBar = new MenuBar(
			fileMenu, rotateMenu, colorAdjustMenu);
		root.setTop(menuBar);

		// Create the center content of the root pane (Border)
		// Make sure the center content is under the menu bar
		BorderPane.setAlignment(mainContentPane, Pos.TOP_CENTER);
		root.setCenter(mainContentPane);

		// When node are visible they can be repositioned
		stage.setOnShown(event -> 
			wireupUIBehavior(stage, progressIndicator));
		
		stage.show();
	}

	@Override
	public void stop() throws Exception {
		super.stop();
		// Shutdown thread service
		executorService.shutdown();
	}

	public static void main(String args[]) {
		launch(args);
	}

	private ImageView createImageView(Rotate rotate) {
		ImageView imageView = new ImageView();
		imageView.setPreserveRatio(true);
		imageView.setSmooth(true);
		imageView.getTransforms().addAll(rotate);
		return imageView;
	}

	private ProgressIndicator createProgressIndicator() {
		ProgressIndicator progress = new ProgressIndicator();
		progress.setVisible(false);
		progress.setMaxSize(100d, 100d);
		return progress;
	}

	private Menu createFileMenu(Stage stage, ProgressIndicator progressIndicator) {
		Menu fileMenu = new Menu("File");
		MenuItem loadImageMenuItem = new MenuItem("_Open");
		loadImageMenuItem.setMnemonicParsing(true);
		loadImageMenuItem.setAccelerator(new KeyCodeCombination(KeyCode.O,
			KeyCombination.SHORTCUT_DOWN));

		// file chooser to open a file
		wireupLoadMenuItem(loadImageMenuItem, stage, progressIndicator);

		MenuItem saveAsMenuItem = new MenuItem("Save _As");
		saveAsMenuItem.setMnemonicParsing(true);

		// file chooser to save image as file
		wireupSaveMenuItem(saveAsMenuItem, stage);

		// Quit application
		MenuItem exitMenuItem = new MenuItem("_Quit");
		exitMenuItem.setMnemonicParsing(true);
		exitMenuItem.setAccelerator(new KeyCodeCombination(KeyCode.Q,
			KeyCombination.SHORTCUT_DOWN));

		// exiting
		exitMenuItem.setOnAction(actionEvent -> Platform.exit());

		fileMenu.getItems().addAll(loadImageMenuItem,
			saveAsMenuItem, exitMenuItem);

		return fileMenu;
	}

	protected void wireupLoadMenuItem(MenuItem menuItem, 
		Stage stage, 
		ProgressIndicator progressIndicator) {
		// A file chooser is launched with a filter based
		// on image file formats
		FileChooser fileChooser = new FileChooser();
		fileChooser.setTitle("View Pictures");
		fileChooser.setInitialDirectory(
			new File(System.getProperty("user.home"))
		);

		fileChooser.getExtensionFilters().addAll(
			new FileChooser.ExtensionFilter("All Images",
				"*.jpg", "*.jpeg", "*.png", "*.bmp", "*.gif"),
				new FileChooser.ExtensionFilter("JPG", "*.jpg"),
				new FileChooser.ExtensionFilter("JPEG", ".jpeg"),
				new FileChooser.ExtensionFilter("PNG", "*.png"),
				new FileChooser.ExtensionFilter("BMP", "*.bmp"),
				new FileChooser.ExtensionFilter("GIF", "*.gif")
		);
		menuItem.setOnAction( actionEnvet -> {
			List<File> list = fileChooser.showOpenMultipleDialog(stage);
			if(list != null) {
				for(File file : list) {
					try {
						String url = file.toURI().toURL().toString();
						if(isValidImageFile(url)) {
							buttonPanel.addImage(url);
							loadAndDisplayImage(progressIndicator);
						}
					}catch(MalformedURLException e) {
						e.printStackTrace();
					}
				}
			}
		});
	}

	protected void wireupSaveMenuItem(MenuItem menuItem, Stage stage) {
		menuItem.setOnAction(event -> {
			FileChooser fileChooser = new FileChooser();
			File fileSave = fileChooser.showSaveDialog(stage);
			if(fileSave != null) {
				WritableImage image = currentViewImage.snapshot(
					 new SnapshotParameters(), null);
				try {
					ImageIO.write(SwingFXUtils.fromFXImage(image, null),
						"png", fileSave);
				} catch(IOException e) {
					e.printStackTrace();
				}
			}
		});
	}

	private Menu createRotateMenu() {
		Menu rotateMenu = new Menu("Rotate");
		// Menu item with a keyboard combo to rotate the image
		// left 90 degrees
		MenuItem rotateLeft = new MenuItem("Rotate 90° Left");
		rotateLeft.setAccelerator(new KeyCodeCombination(KeyCode.LEFT,
			KeyCombination.SHORTCUT_DOWN));

		wireupRotateAngleBy(rotateLeft, -90);

		// Menu item with a keyboard combo to rotate the image
		// right 90 degrees
		MenuItem rotateRight = new MenuItem("Rotate 90° Right");
		rotateLeft.setAccelerator(new KeyCodeCombination(KeyCode.RIGHT,
			KeyCombination.SHORTCUT_DOWN));

		wireupRotateAngleBy(rotateRight, 90);

		rotateMenu.getItems().addAll(rotateLeft, rotateRight);
		return rotateMenu;
	}

	protected void wireupRotateAngleBy(MenuItem menuItem, double angleDegrees) {
		// rotate options
		menuItem.setOnAction(event -> {
			ImageInfo imageInfo = buttonPanel.getCurrentImageInfo();
			imageInfo.addDegrees(angleDegrees);
			rotateImageView(imageInfo.getDegrees());
		});
	}

	private void rotateImageView(double degrees) {
		rotate.setPivotX(currentViewImage.getFitWidth()/2);
		rotate.setPivotY(currentViewImage.getFitHeight()/2);
		rotate.setAngle(degrees);
	}

	private Menu createColorAdjustMenu() {
		Menu colorAdjustMenu = new Menu("Color Adjust");
		Consumer<Double> hueConsumer = (value) ->
			colorAdjust.hueProperty().set(value);
		MenuItem hueMenuItem = createSliderMenuItem("Hue", hueConsumer);

		Consumer<Double> saturationConsumer = (value) ->
			colorAdjust.setSaturation(value);
		MenuItem saturationMenuItem = createSliderMenuItem("Saturation",
			saturationConsumer);

		Consumer<Double> brightnessConsumer = (value) ->
			colorAdjust.setBrightness(value);
		MenuItem brightnessMenuItem = createSliderMenuItem("Brightness",
			brightnessConsumer);

		Consumer<Double> contrastConsumer = (value) ->
			colorAdjust.setContrast(value);
		MenuItem contrastMenuItem = createSliderMenuItem("Contrast",
			contrastConsumer);

		MenuItem resetMenuItem = new MenuItem("Restore to Original");

		resetMenuItem.setOnAction(event -> {
			colorAdjust.setHue(0);
			colorAdjust.setContrast(0);
			colorAdjust.setBrightness(0);
			colorAdjust.setSaturation(0);
			updateSliders();
		});

		colorAdjustMenu.getItems()
			.addAll(hueMenuItem, saturationMenuItem,
				brightnessMenuItem, contrastMenuItem,
				resetMenuItem);

		return colorAdjustMenu;
	}

	private MenuItem createSliderMenuItem(String name, Consumer<Double> c) {
		Slider slider = new Slider(-1, 1, 0);
		sliderLookupMap.put(name, slider);
		slider.valueProperty().addListener(ob ->
			c.accept(slider.getValue()));

		Label label = new Label(name, slider);
		label.setContentDisplay(ContentDisplay.LEFT);
		MenuItem menuItem = new CustomMenuItem(label);
		return menuItem;
	}

	protected void updateSliders() {
		sliderLookupMap.forEach((id, slider) -> {
			switch(id) {
				case "Hue":
					slider.setValue(colorAdjust.getHue());
					break;
				case "Brightness":
					slider.setValue(colorAdjust.getBrightness());
					break;
				case "Saturation":
					slider.setValue(colorAdjust.getSaturation());
					break;
				case "Contrast":
					slider.setValue(colorAdjust.getContrast());
					break;
				default:
					slider.setValue(0);
			}
		});
	}

	private void wireupUIBehavior(Stage stage,
		ProgressIndicator progressIndicator) {
		Scene scene = stage.getScene();

		// make the custom button panel float button right
		Runnable repositionButtonPanel = () -> {
			// update buttonPanel's x
			buttonPanel.setTranslateX(scene.getWidth() - 75);
			// update buttonPanel's y
			buttonPanel.setTranslateY(scene.getHeight() - 75);
		};

		// make the progress indicator float in the center
		Runnable repositionProgressIndicator = () -> {
			// update progress x
			progressIndicator.setTranslateX(
				scene.getWidth()/2 - (progressIndicator.getWidth()/2));
			progressIndicator.setTranslateY(
				scene.getHeight()/2 - (progressIndicator.getHeight()/2));
		};

		Runnable repositionCode = () -> {
			repositionButtonPanel.run();
			repositionProgressIndicator.run();
		};

		// Anytime the window is resized reposition the button panel
		scene.widthProperty().addListener(ob -> 
			repositionCode.run());
		scene.heightProperty().addListener(ob -> 
			repositionCode.run());

		// Go ahead and reposition now
		repositionCode.run();

		// resize image view when scene is resized.
		currentViewImage.fitWidthProperty()
			.bind(scene.widthProperty());

		// view previous image action
		Runnable viewPreviousAction = () -> {
			// if no previous image or currently loading.
			if(buttonPanel.isAtBeginning()) return;
			else buttonPanel.goPrevious();
			loadAndDisplayImage(progressIndicator);
		};

		// attach left button action
		buttonPanel.setLeftButtonAction(mouseEvent ->
			viewPreviousAction.run());

		// Left arrow key stroke pressed action
		scene.addEventHandler(KeyEvent.KEY_PRESSED, keyEvent -> {
			if(keyEvent.getCode() == KeyCode.LEFT
					&& !keyEvent.isShortcutDown()) {
				viewPreviousAction.run();
			}
		});

		// view next image action
		Runnable viewNextAction = () -> {
			// if no next image or currently loading.
			if(buttonPanel.isAtEnd()) return;
			else buttonPanel.goNext();
			loadAndDisplayImage(progressIndicator);
		};

		// attach right button action
		buttonPanel.setRightButtonAction(mouseEvent ->
			viewNextAction.run());

		// Right arrow key stroke pressed action
		scene.addEventHandler(KeyEvent.KEY_PRESSED, keyEvent -> {
			if(keyEvent.getCode() == KeyCode.RIGHT
					&& !keyEvent.isShortcutDown()) {
				viewNextAction.run();
			}
		});

		// Setup drag and drop file capabilities
		setupDragNDrop(stage, progressIndicator);
	}

	protected void loadAndDisplayImage(ProgressIndicator progressIndicator) {
		if(buttonPanel.getCurrentIndex() < 0) return;

		final ImageInfo imageInfo = buttonPanel.getCurrentImageInfo();

		// show spinner while image is loading
		progressIndicator.setVisible(true);

		Task<Image> loadImage = createWorker(imageInfo.getUrl());

		// after loading has succeeded apply image info
		loadImage.setOnSucceeded(workerStateEvent -> {
			try {
				currentViewImage.setImage(loadImage.get());

				// Rotate image view
				rotateImageView(imageInfo.getDegrees());

				// Apply image view
				colorAdjust = imageInfo.getColorAdjust();
				currentViewImage.setEffect(colorAdjust);

				// update the menu items containing slider controls
				updateSliders();
			} catch(InterruptedException e) {
				e.printStackTrace();
			} catch(ExecutionException e) {
				e.printStackTrace();
			} finally {
				// hide progress indicator
				progressIndicator.setVisible(false);
			}
		});

		// any failure turn off spinner
		loadImage.setOnFailed(workerStateEvent -> 
			progressIndicator.setVisible(false));

		executorService.submit(loadImage);
	}

	protected Task<Image> createWorker(String imageUrl) {
		return new Task<Image>() {
			@Override
			protected Image call() throws Exception {
				// On the worker thread...
				Image image = new Image(imageUrl, false);
				return image;
			}
		};
	}

	private void setupDragNDrop(Stage stage, ProgressIndicator progressIndicator) {
		Scene scene = stage.getScene();

		// Dragging over surface
		scene.setOnDragOver((DragEvent event) -> {
			Dragboard db = event.getDragboard();
			if(db.hasFiles()
					|| (db.hasUrl()
					&& isValidImageFile(db.getUrl()))) {
				LOGGER.log(Level.INFO, "url "+db.getUrl());
				event.acceptTransferModes(TransferMode.LINK);
			} else {
				event.consume();
			}
		});

		// Dropping over surface
		scene.setOnDragDropped((DragEvent event) -> {
			Dragboard db = event.getDragboard();
			// image from the local file system.
			if(db.hasFiles() && !db.hasUrl()) {
				db.getFiles().forEach(file -> {
					try {
						String url = file.toURI().toURL().toString();
						if(isValidImageFile(url)) {
							buttonPanel.addImage(url);
						}
					} catch(MalformedURLException e) {
						e.printStackTrace();
					}
				});
			} else {
				String url = db.getUrl();
				LOGGER.log(Level.FINE, "dropped url:"+ db.getUrl());
				if(isValidImageFile(url)) {
					buttonPanel.addImage(url);
				}
			}

			loadAndDisplayImage(progressIndicator);

			event.setDropCompleted(true);
			event.consume();
		});
	}

	private boolean isValidImageFile(String url) {
		List<String> imageTypes = Arrays.asList(".jpg", ".jpeg", ".png", ".gif", ".bmp");
		return imageTypes.stream()
			.anyMatch(t -> url.toLowerCase().endsWith(t));
	}
}
