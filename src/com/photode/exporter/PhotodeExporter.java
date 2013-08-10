/**
 * you can put a one sentence description of your tool here.
 *
 * ##copyright##
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 * 
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General
 * Public License along with this library; if not, write to the
 * Free Software Foundation, Inc., 59 Temple Place, Suite 330,
 * Boston, MA  02111-1307  USA
 * 
 * @author		##author##
 * @modified	##date##
 * @version		##version##
 */

package com.photode.exporter;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Random;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.swing.JFrame;
import javax.swing.JOptionPane;

import processing.app.Base;
import processing.app.Editor;
import processing.app.Sketch;
import processing.app.SketchException;
import processing.app.tools.Tool;
import processing.core.PApplet;
import processing.core.PGraphics;
import processing.core.PGraphicsJava2D;
import processing.core.PImage;
import processing.data.XML;
import processing.mode.java.JavaEditor;
import processing.mode.java.JavaMode;

import com.android.dx.command.dexer.Main.Arguments;

public class PhotodeExporter implements Tool {

	JavaEditor editor;
	JFrame frame;

	public String getMenuTitle() {
		return "Export to Photode...";
	}

	public void init(Editor editor) {
		this.editor = (JavaEditor) editor;
	}

	public void run() {
		export();

	}

	@SuppressWarnings("resource")
	public void export() {
		final Sketch sketch = editor.getSketch();
		if (handleExportCheckModified()) {
			editor.statusNotice("Exporting...");
			try {
				// search for @sketch info
				Pattern pattern = Pattern.compile("\\/\\*\\s*@sketch\\s+((?:[^\\*]|\\*+[^\\*\\/])*)\\*\\/");
				Matcher matcher = pattern.matcher(editor.getText());
				if (matcher.groupCount() != 1)
					throw new IllegalArgumentException("Sketch must have an @sketch directive");

				// CREATE SKETCH INFO XML
				// create output dir
				File outDir = new File(sketch.getFolder().getAbsolutePath() + "/photode/" + sketch.getName());
				outDir.mkdirs();

				String sketchName = null, sketchAuthor = null, sketchDescription = null;
				while (matcher.find()) {
					String[] args = matcher.group(1).split(";");
					for (String arg : args) {

						String[] parts = arg.split("=");
						String key = parts[0].trim();
						String value = parts[1].trim();

						if (key.equals("name"))
							sketchName = value;
						if (key.equals("author"))
							sketchAuthor = value;
						if (key.equals("description"))
							sketchDescription = value;
					}
				}

				if (sketchName == null || sketchAuthor == null || sketchDescription == null)
					throw new IllegalArgumentException("@sketch must contain name, author and description");

				XML sketchInfoXml = new XML("sketch");

				XML nameXml = sketchInfoXml.addChild("name");
				nameXml.setContent(sketchName);
				XML authorXml = sketchInfoXml.addChild("author");
				authorXml.setContent(sketchAuthor);
				XML descriptionXml = sketchInfoXml.addChild("description");
				descriptionXml.setContent(sketchDescription);
				XML typeXml = sketchInfoXml.addChild("type");
				typeXml.setContent("single");
				XML guidXml = sketchInfoXml.addChild("guid");
				guidXml.setContent(UUID.randomUUID().toString());
				XML classNameXml = sketchInfoXml.addChild("className");
				classNameXml.setContent(sketch.getName());
				
				XML paramsXml = null;

				// search for parameters
				pattern = Pattern.compile("\\/\\*\\s*@parameter\\s+((?:[^\\*]|\\*+[^\\*\\/])*)\\*\\/[\\t\\n\\r\\s]*(\\w*)\\s*([\\w\\d]*)");
				matcher = pattern.matcher(editor.getText());

				ArrayList<Parameter> parameters = new ArrayList<Parameter>();

				while (matcher.find()) {
					String paramName = null, paramDescription = null, paramType, paramFieldName, paramMin = null, paramMax = null, paramOptions = null;
					paramType = matcher.group(2).trim();
					if (!(paramType.equals("int") || paramType.equals("float") || paramType.equals("String")))
						throw new IllegalArgumentException("Parameters must be of type int, float or String");

					paramFieldName = matcher.group(3);

					String[] args = matcher.group(1).split(";");

					for (String arg : args) {
						String[] parts = arg.split("=");
						String key = parts[0].trim();
						String value = parts[1].trim();

						if (key.equals("name"))
							paramName = value;
						if (key.equals("description"))
							paramDescription = value;
						if (key.equals("min"))
							paramMin = value;
						if (key.equals("max"))
							paramMax = value;
						if (key.equals("options"))
							paramOptions = value;
					}

					if (paramType.equals("String")) {
						if (paramName == null || paramDescription == null || paramOptions == null)
							throw new IllegalArgumentException("A String @parameter must contain name, description and options");
					} else {
						if (paramName == null || paramDescription == null || paramMin == null || paramMax == null)
							throw new IllegalArgumentException("An int or float @parameter must contain name, description, min and max");

						try {
							Float.parseFloat(paramMin);
							Float.parseFloat(paramMax);
						} catch (NumberFormatException e) {
							throw new IllegalArgumentException("min and max must be of type 'int' or 'float'");
						}
					}

					if (paramsXml == null)
						paramsXml = sketchInfoXml.addChild("parameters");
					XML paramXml = paramsXml.addChild("parameter");
					XML paramNameXml = paramXml.addChild("name");
					paramNameXml.setContent(paramName);

					XML paramDescriptionXml = paramXml.addChild("description");
					paramDescriptionXml.setContent(paramDescription);

					XML paramTypeXml = paramXml.addChild("type");
					paramTypeXml.setContent(paramType);

					if (paramType.equals("String")) {
						XML paramOptionsXml = paramXml.addChild("options");
						paramOptionsXml.setContent(paramOptions);
					} else {
						XML paramMinXml = paramXml.addChild("min");
						paramMinXml.setContent(paramMin);

						XML paramMaxXml = paramXml.addChild("max");
						paramMaxXml.setContent(paramMax);
					}
					XML paramFieldXml = paramXml.addChild("variable");
					paramFieldXml.setContent(paramFieldName);

					parameters.add(new Parameter(paramName, paramDescription, paramType, paramMin, paramMax, paramFieldName, paramOptions));
				}

				PrintWriter pw = new PrintWriter(new File(outDir + "/sketch.def"));
				// pw.write("<!-- WARNING: Don't edit this file directly as you can break the sketch. Instead edit the @sketch and @parameter directives inside Processing then export it again. -->\n");
				pw.write(sketchInfoXml.format(2));
				pw.close();

				// EXPORT SKETCH
				((JavaMode) editor.getMode()).handleExportApplication(sketch);

				// find jar file
				ArrayList<File> jarFiles = new ArrayList<File>();
				search(sketch.getFolder().getAbsolutePath(), jarFiles, new FilenameFilter() {
					@Override
					public boolean accept(File dir, String name) {
						// TODO Auto-generated method stub
						return name.equals(sketch.getName() + ".jar");
					}
				});

				if (jarFiles.size() == 0)
					throw new IOException("Can't locate '" + sketch.getName() + ".jar'");

				File jarFile = jarFiles.get(0);

				// load it
				URLClassLoader classLoader = new URLClassLoader(new URL[] { jarFile.toURI().toURL() }, this.getClass().getClassLoader());
				Class<?> clazz = classLoader.loadClass(sketch.getName());
				// classLoader.close();

				// CREATE ICON
				PApplet applet = (PApplet) clazz.newInstance();
				applet.init();
				Method processImageMethod = applet.getClass().getDeclaredMethod("processImage", PImage.class, PGraphics.class);
				if (processImageMethod.getReturnType() != void.class)
					throw new IllegalArgumentException("processImage must return void");

				// create 4 random version
				Random rnd = new Random(0);
				PGraphics canvas = null;
				for (int i = 0; i < 4; i++) {
					PImage icon = applet.loadImage(Base.getSketchbookToolsFolder() + "/PhotodeExporter/data/logo.png");
					canvas = new PGraphicsJava2D();
					canvas.setPrimary(false);
					canvas.setSize(icon.width, icon.height);

					canvas.beginDraw();
					canvas.background(0);

					// randomize params
					for (Parameter parameter : parameters) {
						Field field = applet.getClass().getDeclaredField(parameter.fieldName);
						field.setAccessible(true);

						if (parameter.type.equals("String")) {
							String option = parameter.options[(int) (rnd.nextFloat() * parameter.options.length)];
							field.set(applet, option);
						} else {
							float value = rnd.nextFloat() * (parameter.max - parameter.min) + parameter.min;

							if (parameter.type.equals("int"))
								field.set(applet, (int) value);
							else if (parameter.type.equals("float"))
								field.set(applet, value);
						}
					}

					// float memoryUseBefore = getMemoryUse();

					processImageMethod.invoke(applet, icon, canvas);

					// float memoryUseAfter = getMemoryUse();

					canvas.endDraw();
					canvas.updatePixels();
					// if (i == 0) {
					// float memoryUse = memoryUseAfter - memoryUseBefore;
					// System.out.println(memoryUse);
					// if (memoryUse > 16) {
					// throw new InvocationTargetException(null,
					// "Sketch uses too much memory: " + memoryUse +
					// "MB. Should be less than 16MB.");
					// }
					// }
					canvas.save(outDir + "/icon_" + (i + 1) + ".jpg");
				}

				PGraphicsJava2D resizeCanvas = new PGraphicsJava2D();
				resizeCanvas.setPrimary(false);
				resizeCanvas.setSize(128, 128);
				resizeCanvas.beginDraw();
				resizeCanvas.smooth();
				resizeCanvas.image(canvas, 0, 0, resizeCanvas.width, resizeCanvas.height);
				resizeCanvas.endDraw();
				resizeCanvas.save(outDir + "/icon_small.jpg");

				// DEXIFY
				File dexJar = new File(outDir + "/sketch.jar");
				Arguments arguments = new Arguments();
				arguments.parse(new String[] { "--output=" + dexJar.getAbsolutePath(), jarFile.getAbsolutePath() });
				int result = com.android.dx.command.dexer.Main.run(arguments);
				if (result != 0)
					throw new IOException("Error creating dexed jar");

				File distDir = new File(sketch.getFolder().getAbsolutePath() + "/photode/distribution");
				distDir.mkdirs();

				// CREATE ZIP
				byte[] buf = new byte[1024];
				FileInputStream in;
				File sketchFile = new File(distDir + "/" + sketch.getName() + ".photode");
				ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(sketchFile));
				int len;

				// add icons
				for (int i = 0; i < 4; i++) {
					in = new FileInputStream(outDir + "/icon_" + (i + 1) + ".jpg");
					zip.putNextEntry(new ZipEntry("icon_" + (i + 1) + ".jpg"));
					while ((len = in.read(buf)) > 0)
						zip.write(buf, 0, len);
					zip.closeEntry();
					in.close();
				}
				in = new FileInputStream(outDir + "/icon_small.jpg");
				zip.putNextEntry(new ZipEntry("icon_small.jpg"));
				while ((len = in.read(buf)) > 0)
					zip.write(buf, 0, len);
				zip.closeEntry();
				in.close();

				// add dex
				in = new FileInputStream(dexJar);
				zip.putNextEntry(new ZipEntry(dexJar.getName()));
				while ((len = in.read(buf)) > 0)
					zip.write(buf, 0, len);
				zip.closeEntry();
				in.close();

				// add sketch.def
				in = new FileInputStream(outDir + "/sketch.def");
				zip.putNextEntry(new ZipEntry("sketch.def"));
				while ((len = in.read(buf)) > 0)
					zip.write(buf, 0, len);
				zip.closeEntry();
				in.close();

				// add source
				final ArrayList<File> sourceFiles = new ArrayList<File>();
				final ArrayList<String> sourceFileNames = new ArrayList<String>();
				search(sketch.getFolder().getAbsolutePath(), sourceFiles, new FilenameFilter() {
					@Override
					public boolean accept(File dir, String name) {
						return name.endsWith(".pde");
					}
				});

				for (File sourceFile : sourceFiles) {
					if (sourceFileNames.contains(sourceFile.getName()))
						continue;

					sourceFileNames.add(sourceFile.getName());
					in = new FileInputStream(sourceFile);
					zip.putNextEntry(new ZipEntry("source/" + sourceFile.getName()));
					while ((len = in.read(buf)) > 0)
						zip.write(buf, 0, len);
					zip.closeEntry();
					in.close();
				}

				zip.close();

				editor.statusNotice("Done exporting.");

				Base.openFolder(outDir);
			} catch (IOException e) {
				editor.statusNotice("Error during Photode export. See console for more information.");
				System.out.println(e.getMessage());
				e.printStackTrace();
			} catch (SketchException e) {
				editor.statusNotice("Error during Photode export. See console for more information.");
				System.out.println(e.getMessage());
				e.printStackTrace();
			} catch (IllegalAccessException e) {
				editor.statusNotice("Error during Photode export. See console for more information.");
				System.out.println(e.getMessage());
				e.printStackTrace();
			} catch (IllegalArgumentException e) {
				editor.statusNotice("Error during Photode export. See console for more information.");
				e.printStackTrace();
			} catch (InvocationTargetException e) {
				editor.statusNotice("Error during Photode export. See console for more information.");
				System.out.println("Error in the sketch code: " + e.getMessage());
				e.printStackTrace();
			} catch (ClassNotFoundException e) {
				editor.statusNotice("Error during Photode export. See console for more information.");
				e.printStackTrace();
			} catch (InstantiationException e) {
				editor.statusNotice("Error during Photode export. See console for more information.");
				e.printStackTrace();
			} catch (NoSuchMethodException e) {
				editor.statusNotice("Error during Photode export. See console for more information.");
				System.out.println("Sketch must have a 'void processImage(PImage in, PGraphics out)' method defined.");
			} catch (SecurityException e) {
				editor.statusNotice("Error during Photode export. See console for more information.");
				e.printStackTrace();
			} catch (NoSuchFieldException e) {
				editor.statusNotice("Error during Photode export. See console for more information.");
				e.printStackTrace();
			}
		}

	}

	protected boolean handleExportCheckModified() {
		if (editor.getSketch().isModified()) {
			Object[] options = { "OK", "Cancel" };
			int result = JOptionPane.showOptionDialog(editor, "Save changes before export?", "Save", JOptionPane.OK_CANCEL_OPTION,
					JOptionPane.QUESTION_MESSAGE, null, options, options[0]);

			if (result == JOptionPane.OK_OPTION) {
				editor.handleSave(true);

			} else {
				// why it's not CANCEL_OPTION is beyond me (at least on the mac)
				// but f-- it.. let's get this shite done..
				// } else if (result == JOptionPane.CANCEL_OPTION) {
				editor.statusNotice("Export canceled, changes must first be saved.");
				// toolbar.clear();
				return false;
			}
		}
		return true;
	}

	private void search(String path, ArrayList<File> foundFiles, FilenameFilter filter) {
		File root = new File(path);
		File[] list = root.listFiles();

		for (File f : list) {
			if (f.isDirectory()) {
				search(f.getAbsolutePath(), foundFiles, filter);
			} else {
				if (filter.accept(f.getAbsoluteFile(), f.getName()))
					foundFiles.add(f);
			}
		}
	}

	public class Parameter {
		String name, description, type, fieldName;
		String[] options;
		float min, max;

		Parameter(String name, String description, String type, String paramMin, String paramMax, String fieldName, String options) {
			this.name = name;
			this.description = description;
			this.type = type;

			this.fieldName = fieldName;
			if (type.equals("String")) {
				this.options = options.split(",");
				for (String option : this.options) {
					option = option.trim();
				}
			} else {
				this.min = Float.parseFloat(paramMin);
				this.max = Float.parseFloat(paramMax);
			}
		}
	}

	private static int fSAMPLE_SIZE = 100;
	private static long fSLEEP_INTERVAL = 100;

	private static float getMemoryUse() {
		putOutTheGarbage();
		long totalMemory = Runtime.getRuntime().totalMemory();

		putOutTheGarbage();
		long freeMemory = Runtime.getRuntime().freeMemory();

		return (totalMemory - freeMemory) / 1000000;
	}

	private static void putOutTheGarbage() {
		collectGarbage();
		collectGarbage();
	}

	private static void collectGarbage() {
		try {
			System.gc();
			Thread.sleep(fSLEEP_INTERVAL);
			System.runFinalization();
			Thread.sleep(fSLEEP_INTERVAL);
		} catch (InterruptedException ex) {
			ex.printStackTrace();
		}
	}
}
