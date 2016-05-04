/*
 * #%L
 * SCIFIO library for reading and converting scientific file formats.
 * %%
 * Copyright (C) 2013 - 2016 Board of Regents of the University of
 * Wisconsin-Madison
 * %%
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDERS OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 * #L%
 */

package io.scif.itk;

import java.awt.image.ColorModel;
import java.awt.image.IndexColorModel;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.Set;

import loci.common.Constants;
import loci.common.DataTools;
import loci.common.DebugTools;
import loci.formats.ChannelFiller;
import loci.formats.FormatException;
import loci.formats.FormatTools;
import loci.formats.IFormatReader;
import loci.formats.IFormatWriter;
import loci.formats.ImageReader;
import loci.formats.ImageWriter;
import loci.formats.MetadataTools;
import loci.formats.gui.Index16ColorModel;
import loci.formats.meta.IMetadata;
import loci.formats.meta.MetadataStore;

/**
 * SCIFIOITKBridge is a Java console application that listens for "commands" on
 * stdin and issues results on stdout. It is used by the <a
 * href="https://github.com/scifio/scifio-imageio">SCIFIO ImageIO plugin for
 * ITK</a> to read image files.
 *
 * @author Mark Hiner
 * @author Curtis Rueden
 */
public class SCIFIOITKBridge {

	private IFormatReader reader = null;
	private IFormatWriter writer = null;
	private BufferedReader in;
	private String readerPath = "";

	/** Enters an input loop, waiting for commands, until EOF is reached. */
	public boolean waitForInput() throws FormatException, IOException {
		in =
			new BufferedReader(new InputStreamReader(System.in, Constants.ENCODING));
		boolean ret = true;
		while (true) {
			final String line = in.readLine(); // blocks until a line is read
			if (line == null) break; // eof
			ret = ret && executeCommand(line);
		}
		in.close();
		return ret;
	}

	/**
	 * Executes the given command line. The following commands are supported:
	 * <ul>
	 * <li>info</li> - Dumps image metadata
	 * <li>read</li> - Dumps image pixels
	 * <li>canRead</li> - Tests whether the given file path can be parsed
	 * </ul>
	 *
	 * @throws FormatException
	 */
	public boolean executeCommand(final String commandLine) throws IOException,
		FormatException
	{
		final String[] args = commandLine.split("\t");

		for (int i = 0; i < args.length; i++) {
			args[i] = args[i].trim();
		}

		return executeCommand(args);
	}

	private boolean executeCommand(final String[] args) throws FormatException,
		IOException
	{
		boolean success = false;

		String series =
			reader == null || reader.getCurrentFile() == null ? "0" : Integer
				.toString(reader.getSeries());
		String id = "";
		String[] idTokens = null;

		if (args.length > 1) {
			idTokens = args[1].split("@");
			id = idTokens[0];

			if (idTokens.length == 3) {
				id += idTokens[2];
				series = idTokens[1];
			}
		}

		if (args[0].equals("info")) {
			success = readImageInfo(id, series);
		}
		else if (args[0].equals("series")) {
			success = setSeries(args[1]);
		}
		else if (args[0].equals("seriesCount")) {
			success = getSeriesCount();
		}
		else if (args[0].equals("read")) {
			final int xBegin = Integer.parseInt(args[2]);
			final int xEnd = Integer.parseInt(args[3]) + xBegin - 1;
			final int yBegin = Integer.parseInt(args[4]);
			final int yEnd = Integer.parseInt(args[5]) + yBegin - 1;
			final int zBegin = Integer.parseInt(args[6]);
			final int zEnd = Integer.parseInt(args[7]) + zBegin - 1;
			final int tBegin = Integer.parseInt(args[8]);
			final int tEnd = Integer.parseInt(args[9]) + tBegin - 1;
			final int cBegin = Integer.parseInt(args[10]);
			final int cEnd = Integer.parseInt(args[11]) + cBegin - 1;
			success =
				read(id, series, xBegin, xEnd, yBegin, yEnd, zBegin, zEnd, tBegin,
					tEnd, cBegin, cEnd);
		}
		else if (args[0].equals("canRead")) {
			success = canRead(id);
		}
		else if (args[0].equals("canWrite")) {
			success = canWrite(id);
		}
		else if (args[0].equals("waitForInput")) {
			success = waitForInput();
		}
		else if (args[0].equals("write")) {
			final int byteOrder = Integer.parseInt(args[2]);
			final int dims = Integer.parseInt(args[3]);
			final int dimx = Integer.parseInt(args[4]);
			final int dimy = Integer.parseInt(args[5]);
			final int dimz = Integer.parseInt(args[6]);
			final int dimt = Integer.parseInt(args[7]);
			final int dimc = Integer.parseInt(args[8]);
			final double pSizeX = Double.parseDouble(args[9]);
			final double pSizeY = Double.parseDouble(args[10]);
			final double pSizeZ = Double.parseDouble(args[11]);
			final double pSizeT = Double.parseDouble(args[12]);
			final double pSizeC = Double.parseDouble(args[13]);
			final int pixelType = Integer.parseInt(args[14]);
			final int rgbCCount = Integer.parseInt(args[15]);
			final int xStart = Integer.parseInt(args[16]);
			final int yStart = Integer.parseInt(args[18]);
			final int zStart = Integer.parseInt(args[20]);
			final int tStart = Integer.parseInt(args[22]);
			final int cStart = Integer.parseInt(args[24]);
			final int xCount = Integer.parseInt(args[17]);
			final int yCount = Integer.parseInt(args[19]);
			final int zCount = Integer.parseInt(args[21]);
			final int tCount = Integer.parseInt(args[23]);
			final int cCount = Integer.parseInt(args[25]);

			ColorModel cm = null;
			final int useCM = Integer.parseInt(args[26]);
			if (useCM == 1) cm = buildColorModel(args, byteOrder);

			success =
				write(id, cm, byteOrder, dims, dimx, dimy, dimz, dimt, dimc, pSizeX,
					pSizeY, pSizeZ, pSizeT, pSizeC, pixelType, rgbCCount, xStart, yStart,
					zStart, tStart, cStart, xCount, yCount, zCount, tCount, cCount);
		}
		else {
			throw new FormatException("Error: unknown command: " + args[0]);
		}

		if (!success) {
			String command = "";
			for (final String s : args)
				command += (s + "\t");
			printAndFlush(System.err, "Command failure:\n" + command + "\n");
		}

		return success;
	}

	/**
	 * Sets the series of the current reader.
	 *
	 * @param series Series index within the current dataset
	 * @return False if the current reader is null.
	 */
	public boolean setSeries(final String series) throws IOException {
		final int newSeries = Integer.parseInt(series);
		if (reader == null) {
			printAndFlush(System.out, "Reader null. Could not set series.\n");
		}
		else if (newSeries >= reader.getSeriesCount()) {
			printAndFlush(System.out, "Series index: " + newSeries +
				" out of bounds: " + reader.getSeriesCount() + "\n");
		}
		else {
			reader.setSeries(newSeries);

			printAndFlush(System.out, "Set series " + series + "\n");
		}

		return true;
	}

	/**
	 * Pipes the series count of the current reader.
	 *
	 * @return False if the current reader is null.
	 */
	public boolean getSeriesCount() throws IOException {
		if (reader == null) {
			printAndFlush(System.out, "Reader null. Could not get series.\n");
		}
		else {
			printAndFlush(System.out, Integer.toString(reader.getSeriesCount()) +
				"\n");
		}

		return true;
	}

	/**
	 * Reads image metadata from the given file path, dumping the resultant values
	 * to stdout in a specific order (which we have not documented here because we
	 * are lazy).
	 *
	 * @param filePath a path to a file on disk, or a hash token for an
	 *          initialized reader (beginning with "hash:") as given by a call to
	 *          "info" earlier.
	 */
	public boolean readImageInfo(final String filePath, final String series)
		throws FormatException, IOException
	{
		createReader(filePath);

		final int oldSeries = reader.getSeries();
		if (!series.equalsIgnoreCase("all")) reader.setSeries(Integer
			.parseInt(series));

		final MetadataStore store = reader.getMetadataStore();
		final IMetadata meta = (IMetadata) store;

		// now print the informations

		// interleaved?
		sendData("Interleaved", String.valueOf(reader.isInterleaved() ? 1 : 0));

		// little endian?
		sendData("LittleEndian", String.valueOf(reader.isLittleEndian() ? 1 : 0));

		// component type
		// set ITK component type
		final int pixelType = reader.getPixelType();
		sendData("PixelType", String.valueOf(pixelType));

		// x, y, z, t, c
		sendData("SizeX", String.valueOf(reader.getSizeX()));
		sendData("SizeY", String.valueOf(reader.getSizeY()));
		sendData("SizeZ", String.valueOf(reader.getSizeZ()));
		sendData("SizeT", String.valueOf(reader.getSizeT()));
		sendData("SizeC", String.valueOf(reader.getEffectiveSizeC()));

		// number of components
		sendData("RGBChannelCount", String.valueOf(reader.getRGBChannelCount()));

		// spacing
		// Note: ITK X,Y,Z spacing is mm. Bio-Formats uses um.
		sendData("PixelsPhysicalSizeX", String.valueOf(((meta
			.getPixelsPhysicalSizeX(0) == null ? 1.0 : meta.getPixelsPhysicalSizeX(0)
			.getValue()) / 1000f)));
		sendData("PixelsPhysicalSizeY", String.valueOf(((meta
			.getPixelsPhysicalSizeY(0) == null ? 1.0 : meta.getPixelsPhysicalSizeY(0)
			.getValue()) / 1000f)));
		sendData("PixelsPhysicalSizeZ", String.valueOf(((meta
			.getPixelsPhysicalSizeZ(0) == null ? 1.0 : meta.getPixelsPhysicalSizeZ(0)
			.getValue()) / 1000f)));
		sendData("PixelsPhysicalSizeT", String
			.valueOf((meta.getPixelsTimeIncrement(0) == null ? 1.0 : meta
				.getPixelsTimeIncrement(0))));
		sendData("PixelsPhysicalSizeC", String.valueOf(1.0));

		final HashMap<String, Object> metadata = new HashMap<String, Object>();
		metadata.putAll(reader.getGlobalMetadata());
		metadata.putAll(reader.getSeriesMetadata());
		final Set<Entry<String, Object>> entries = metadata.entrySet();
		final Iterator<Entry<String, Object>> it = entries.iterator();

		while (it.hasNext()) {
			final Entry<String, Object> entry = it.next();

			final String key = entry.getKey();
			String value = entry.getValue().toString();

			// remove the line return
			value = value.replace("\\", "\\\\").replace("\n", "\\n");
			sendData(key, value);
		}

		// lookup table
		// NB: if there are formats that don't preserve the LUT,
		// put this logic in Read() or open a plane in this method to force
		// population
		// reader.openPlane(0, 0, 0, 0, 0);

		final boolean use16 = reader.get16BitLookupTable() != null;
		final boolean use8 = reader.get8BitLookupTable() != null;

		if (use16 || use8) {
			printAndFlush(System.err, "Saving color model...\n");

			sendData("UseLUT", String.valueOf(true));
			sendData("LUTBits", String.valueOf(use8 ? 8 : 16));
			final short[][] lut16 = reader.get16BitLookupTable();
			final byte[][] lut8 = reader.get8BitLookupTable();

			sendData("LUTLength", String.valueOf(use8 ? lut8[0].length
				: lut16[0].length));

			for (int i = 0; i < (use8 ? lut8.length : lut16.length); i++) {

				char channel;

				switch (i) {
					case 0:
						channel = 'R';
						break;
					case 1:
						channel = 'G';
						break;
					case 2:
						channel = 'B';
						break;
					default:
						channel = ' ';
				}

				for (int j = 0; j < (use8 ? lut8[0].length : lut16[0].length); j++) {
					sendData("LUT" + channel + "" + j, String.valueOf(use8 ? lut8[i][j]
						: lut16[i][j]));
				}
			}
		}
		else sendData("UseLUT", String.valueOf(false));

		System.err.println("I am done reading image information in java");
		printAndFlush(System.out, "\n");

		reader.setSeries(oldSeries);

		return true;
	}

	/**
	 * Reads image pixels from the given file path, dumping the resultant binary
	 * stream to stdout.
	 *
	 * @param filePath a path to a file on disk, or a hash token for an
	 *          initialized reader (beginning with "hash:") as given by a call to
	 *          "info" earlier. Using a hash token eliminates the need to
	 *          initialize the file a second time with a fresh reader object.
	 *          Regardless, after reading the file, the reader closes the file
	 *          handle, and invalidates its hash token.
	 */
	public boolean read(final String filePath, final String series,
		final int xBegin, final int xEnd, final int yBegin, final int yEnd,
		final int zBegin, final int zEnd, final int tBegin, final int tEnd,
		final int cBegin, final int cEnd) throws FormatException, IOException
	{
		createReader(filePath);

		final int oldSeries = reader.getSeries();
		if (!series.equalsIgnoreCase("all")) reader.setSeries(Integer
			.parseInt(series));

		final int rgbChannelCount = reader.getRGBChannelCount();
		final int bpp = FormatTools.getBytesPerPixel(reader.getPixelType());
		final int xCount = reader.getSizeX();
		final int yCount = reader.getSizeY();

		final boolean isInterleaved = reader.isInterleaved();
		final boolean canDoDirect =
			xBegin == 0 && yBegin == 0 && xEnd == xCount - 1 && yEnd == yCount - 1 &&
				rgbChannelCount == 1;

		final BufferedOutputStream out =
			new BufferedOutputStream(System.out, 100 * 1024 * 1024);
		// System.err.println("canDoDirect = "+canDoDirect);

		final byte[] pixel = new byte[bpp];
		byte[] image = null;
		for (int c = cBegin; c <= cEnd; c++) {
			for (int t = tBegin; t <= tEnd; t++) {
				for (int z = zBegin; z <= zEnd; z++) {
					final int xLen = xEnd - xBegin + 1;
					final int yLen = yEnd - yBegin + 1;
					final int no = reader.getIndex(z, c, t);
					if (image == null) {
						// allocate a new image buffer
						image = reader.openBytes(no, xBegin, yBegin, xLen, yLen);
					}
					else {
						// reuse the existing image buffer
						reader.openBytes(no, image, xBegin, yBegin, xLen, yLen);
					}
					if (canDoDirect) {
						final Object data =
							DataTools.makeDataArray(image, bpp, FormatTools
								.isFloatingPoint(reader.getPixelType()), reader
								.isLittleEndian());
						out.write(getBytes(data));
					}
					else {
						for (int y = 0; y < yLen; y++) {
							for (int x = 0; x < xLen; x++) {
								for (int i = 0; i < rgbChannelCount; i++) {
									for (int b = 0; b < bpp; b++) {
										int index = 0;
										if (isInterleaved) {
											index = ((y * xLen + x) * rgbChannelCount + i) * bpp + b;
										}
										else {
											index = ((i * yLen + y) * xLen + x) * bpp + b;
										}
										pixel[b] = image[index];
									}
									final Object data =
										DataTools.makeDataArray(pixel, bpp, FormatTools
											.isFloatingPoint(reader.getPixelType()), reader
											.isLittleEndian());
									out.write(getBytes(data));
								}
							}
						}
					}
				}
			}
		}
		out.flush();

		reader.setSeries(oldSeries);

		return true;
	}

	private byte[] getBytes(final Object data) {
		if (data instanceof byte[]) {
			return (byte[]) data;
		}
		else if (data instanceof short[]) {
			return DataTools.shortsToBytes((short[]) data, true);
		}
		else if (data instanceof int[]) {
			return DataTools.intsToBytes((int[]) data, true);
		}
		else if (data instanceof long[]) {
			return DataTools.longsToBytes((long[]) data, true);
		}
		else if (data instanceof double[]) {
			return DataTools.doublesToBytes((double[]) data, true);
		}
		else if (data instanceof float[]) {
			return DataTools.floatsToBytes((float[]) data, true);
		}
		return null;
	}

	/**
	 * Writes data to the given fileName.
	 * 
	 * @param fileName
	 * @param cm
	 * @param byteOrder
	 * @param dims
	 * @param dimx
	 * @param dimy
	 * @param dimz
	 * @param dimt
	 * @param dimc
	 * @param pSizeX
	 * @param pSizeY
	 * @param pSizeZ
	 * @param pSizeT
	 * @param pSizeC
	 * @param pixelType
	 * @param rgbCCount
	 * @param xStart
	 * @param yStart
	 * @param zStart
	 * @param tStart
	 * @param cStart
	 * @param xCount
	 * @param yCount
	 * @param zCount
	 * @param tCount
	 * @param cCount
	 * @return True if the write succeeded (which is always, unless an exception
	 *         is thrown).
	 * @throws IOException Thrown if something goes wrong writing the data.
	 * @throws FormatException Never actually thrown.
	 */
	public boolean write(final String fileName, final ColorModel cm,
		final int byteOrder, final int dims, final int dimx, final int dimy,
		final int dimz, final int dimt, final int dimc, final double pSizeX,
		final double pSizeY, final double pSizeZ, final double pSizeT,
		final double pSizeC, final int pixelType, final int rgbCCount,
		final int xStart, final int yStart, final int zStart, final int tStart,
		final int cStart, final int xCount, final int yCount, final int zCount,
		final int tCount, final int cCount) throws IOException, FormatException
	{
		final IMetadata meta = MetadataTools.createOMEXMLMetadata();
		MetadataTools.populateMetadata(meta, 0, fileName, byteOrder == 0, "XYZTC",
			FormatTools.getPixelTypeString(pixelType), dimx, dimy, dimz, dimc *
				rgbCCount, dimt, rgbCCount);

		writer = new ImageWriter();
		writer.setMetadataRetrieve(meta);
		writer.setId(fileName);

		// Assume the data was stored as itk::RGBPixels and is thus interleaved
		if (rgbCCount > 1) writer.setInterleaved(true);

		// build color model
		if (cm != null) {
			printAndFlush(System.err, "Using color model...\n");
			writer.setColorModel(cm);
		}

		// maybe this isn't enough...
		printAndFlush(System.err, "Using writer for format: " + writer.getFormat() +
			"n");

		final int bpp = FormatTools.getBytesPerPixel(pixelType);

		final int bytesPerPlane =
			(xCount - xStart) * (yCount - yStart) * bpp * rgbCCount;

		final int numIters =
			(cCount - cStart) * (tCount - tStart) * (zCount - zStart);

		// tell native code how many times to iterate & how big each iteration is
		printAndFlush(System.out, bytesPerPlane + "\n" + numIters + "\n" +
			fileName + "\n" + cStart + "\n" + cCount + "\n" + tStart + "\n" + tCount +
			"\n" + zStart + "\n" + zCount + "\n");

		final byte[] buf = new byte[bytesPerPlane];
		final BufferedInputStream linein = new BufferedInputStream(System.in);
		int no = 0;
		for (int c = cStart; c < cStart + cCount; c++) {
			for (int t = tStart; t < tStart + tCount; t++) {
				for (int z = zStart; z < zStart + zCount; z++) {

					int bytesRead = 0;

					while (bytesRead < bytesPerPlane) {
						final int read =
							linein.read(buf, bytesRead, bytesPerPlane - bytesRead);
						bytesRead += read > 0 ? read : 0;
						// notify native code that more bytes can be read
						printAndFlush(System.out, "Bytes read: " +
							bytesRead +
							". " +
							(bytesRead < bytesPerPlane ? "Ready for more bytes"
								: "Done reading bytes") + ".\n");
					}

					writer.saveBytes(no, buf, xStart, yStart, xCount, yCount);

					// Hand-shake with C++, make sure it got the notification that bytes
					// are done.
					linein.read(buf);
					// notify native code that a plane has been saved
					printAndFlush(System.out, "Plane no: " + no + " saved.\n");
					no++;
				}
			}
		}

		if (writer != null) writer.close();

		// Hand-shake with C++, make sure it got the notification that the plane is
		// done.
		linein.read(buf);
		// notify native code the image is complete
		printAndFlush(System.out, "Done writing image: " + fileName + "\n");

		return true;
	}

	/** Tests whether the given file path can be parsed by Bio-Formats. */
	public boolean canRead(final String filePath) throws FormatException,
		IOException
	{
		createReader(null);
		final boolean canRead = reader.isThisType(filePath);
		printAndFlush(System.out, String.valueOf(canRead) + "\n");
		return true;
	}

	/**
	 * Tests whether the given file path can be written by Bio-Formats.
	 *
	 * @param filePath The file path to test.
	 * @return True if the file path can be written by Bio-Formats.
	 * @throws FormatException Never thrown.
	 * @throws IOException If something goes wrong testing the file.
	 */
	public boolean canWrite(final String filePath) throws FormatException,
		IOException
	{
		writer = new ImageWriter();
		final boolean canWrite = writer.isThisType(filePath);
		printAndFlush(System.out, String.valueOf(canWrite) + "\n");
		return true;
	}

	private IFormatReader createReader(final String filePath)
		throws FormatException, IOException
	{
		if (readerPath != null && readerPath.equals(filePath)) {
			// just use the existing reader
			return reader;
		}

		if (reader != null) {
			reader.close();
		}
		System.err.println("Creating new reader for " + filePath);
		// initialize a fresh reader
		final ChannelFiller cf = new ChannelFiller(new ImageReader());
		cf.setFilled(true);
		reader = cf;
		readerPath = filePath;

		reader.setMetadataFiltered(true);
		reader.setOriginalMetadataPopulated(true);
		final MetadataStore store = MetadataTools.createOMEXMLMetadata();
		if (store == null) System.err.println("OME-Java library not found.");
		else reader.setMetadataStore(store);

		// avoid grouping all the .lsm when a .mdb is there
		reader.setGroupFiles(false);

		if (filePath != null) {
			reader.setId(filePath);
			reader.setSeries(0);
		}

		return reader;
	}

	/**
	 * Cleans up, closing any active reader and writer streams, then terminates
	 * the JVM using {@link System#exit(int)} with the given exit code.
	 * 
	 * @param val The exit code to use when quitting the Java process.
	 * @throws FormatException Never thrown.
	 * @throws IOException If something goes wrong closing the streams.
	 */
	public void exit(final int val) throws FormatException, IOException {
		if (reader != null) reader.close();
		if (writer != null) writer.close();
		if (in != null) in.close();
		System.exit(val);
	}

	/**
	 * Writes the provided message, appends a newline character and flushes.
	 */
	private void printAndFlush(final PrintStream stream, final String message)
		throws IOException
	{
		final BufferedWriter w = new BufferedWriter(new OutputStreamWriter(stream));
		w.write(message + "\n");
		w.flush();
	}

	/**
	 * Pipes the given key, value pair out to C++
	 */
	private void sendData(final String key, String value) throws IOException {
		if (value != null && value.length() > 0) value = "\n" + value;
		printAndFlush(System.out, key + value);
	}

	private ColorModel buildColorModel(final String[] args, final int byteOrder)
		throws IOException
	{
		final int lutBits = Integer.parseInt(args[27]);
		final int lutLength = Integer.parseInt(args[28]);

		ColorModel cm = null;

		if (lutBits == 8) {
			final byte[] r = new byte[lutLength], g = new byte[lutLength], b =
				new byte[lutLength];

			for (int i = 0; i < lutLength; i++) {
				r[i] = Byte.parseByte(args[29 + (3 * i)]);
				g[i] = Byte.parseByte(args[29 + (3 * i) + 1]);
				b[i] = Byte.parseByte(args[29 + (3 * i) + 2]);
			}

			cm = new IndexColorModel(lutBits, lutLength, r, g, b);
		}
		else if (lutBits == 16) {
			final short[][] lut = new short[3][lutLength];

			for (int i = 0; i < lutLength; i++) {
				lut[0][i] = Short.parseShort(args[29 + (3 * i)]);
				lut[1][i] = Short.parseShort(args[29 + (3 * i) + 1]);
				lut[2][i] = Short.parseShort(args[29 + (3 * i) + 2]);
			}

			cm = new Index16ColorModel(lutBits, lutLength, lut, byteOrder == 0);
		}

		return cm;
	}

	// -- Main method --

	public static void main(final String[] args) throws FormatException,
		IOException
	{
		DebugTools.enableLogging("OFF");
		if (!new SCIFIOITKBridge().executeCommand(args)) System.exit(1);
	}
}
