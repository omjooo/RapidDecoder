package agu.bitmap.png;

import java.util.Arrays;
import java.util.zip.CRC32;
import java.util.zip.Inflater;

/**
 * This object process the concatenation of IDAT chunks.
 * <p>
 * It extends {@link DeflatedChunksSet}, adding the intelligence to unfilter
 * rows, and to understand row lenghts in terms of ImageInfo and (eventually)
 * Deinterlacer
 */
public class IdatSet extends DeflatedChunksSet {

	protected byte rowUnfiltered[];
	protected byte rowUnfilteredPrev[];
	protected final ImageInfo imgInfo;
	protected final Deinterlacer deinterlacer;

	final RowInfo rowinfo; // info for the last processed row

	/**
	 * @param id
	 *            Chunk id (first chunk), should be shared by all concatenated
	 *            chunks
	 * @param iminfo
	 *            Image info
	 * @param deinterlacer
	 *            Not null if interlaced
	 */
	public IdatSet(String id, ImageInfo iminfo, Deinterlacer deinterlacer) {
		this(id, iminfo, deinterlacer, null, null);
	}

	/**
	 * Special constructor with preallocated buffer.
	 * <p>
	 * <p>
	 * Same as {@link #IdatSet(String, ImageInfo, Deinterlacer)}, but you can
	 * pass a Inflater (will be reset internally), and a buffer (will be used
	 * only if size is enough)
	 */
	public IdatSet(String id, ImageInfo iminfo, Deinterlacer deinterlacer, Inflater inf, byte[] buffer) {
		super(id, deinterlacer != null ? deinterlacer.getBytesToRead() + 1 : iminfo.bytesPerRow + 1,
				iminfo.bytesPerRow + 1, inf, buffer);
		this.imgInfo = iminfo;
		this.deinterlacer = deinterlacer;
		this.rowinfo = new RowInfo(iminfo, deinterlacer);
	}

	/**
	 * Applies PNG un-filter to inflated raw line. Result in
	 * {@link #getUnfilteredRow()} {@link #getRowLen()}
	 */
	public void unfilterRow() {
		unfilterRow(rowinfo.bytesRow);
	}

	// nbytes: NOT including the filter byte. leaves result in rowUnfiltered
	protected void unfilterRow(int nbytes) {
		if (rowUnfiltered == null || rowUnfiltered.length < row.length) {
			rowUnfiltered = new byte[row.length];
			rowUnfilteredPrev = new byte[row.length];
		}
		if (rowinfo.rowNsubImg == 0)
			Arrays.fill(rowUnfiltered, (byte) 0); // see swap that follows
		// swap
		byte[] tmp = rowUnfiltered;
		rowUnfiltered = rowUnfilteredPrev;
		rowUnfilteredPrev = tmp;

		int ftn = row[0];
		FilterType ft = FilterType.getByVal(ftn);
		rowUnfiltered[0] = row[0]; // we copy the filter type, can be useful 
		if (ft == null)
			throw new PngjInputException("Filter type " + ftn + " invalid");
		switch (ft) {
		case FILTER_NONE:
			unfilterRowNone(nbytes);
			break;
		case FILTER_SUB:
			unfilterRowSub(nbytes);
			break;
		case FILTER_UP:
			unfilterRowUp(nbytes);
			break;
		case FILTER_AVERAGE:
			unfilterRowAverage(nbytes);
			break;
		case FILTER_PAETH:
			unfilterRowPaeth(nbytes);
			break;
		default:
			throw new PngjInputException("Filter type " + ftn + " not implemented");
		}
	}

	private void unfilterRowAverage(final int nbytes) {
		int i, j, x;
		for (j = 1 - imgInfo.bytesPixel, i = 1; i <= nbytes; i++, j++) {
			x = j > 0 ? (rowUnfiltered[j] & 0xff) : 0;
			rowUnfiltered[i] = (byte) (row[i] + (x + (rowUnfilteredPrev[i] & 0xFF)) / 2);
		}
	}

	private void unfilterRowNone(final int nbytes) {
		for (int i = 1; i <= nbytes; i++) {
			rowUnfiltered[i] = (byte) (row[i]);
		}
	}

	private void unfilterRowPaeth(final int nbytes) {
		int i, j, x, y;
		for (j = 1 - imgInfo.bytesPixel, i = 1; i <= nbytes; i++, j++) {
			x = j > 0 ? (rowUnfiltered[j] & 0xFF) : 0;
			y = j > 0 ? (rowUnfilteredPrev[j] & 0xFF) : 0;
			rowUnfiltered[i] = (byte) (row[i] + PngHelperInternal.filterPaethPredictor(x, rowUnfilteredPrev[i] & 0xFF,
					y));
		}
	}

	private void unfilterRowSub(final int nbytes) {
		int i, j;
		for (i = 1; i <= imgInfo.bytesPixel; i++) {
			rowUnfiltered[i] = (byte) (row[i]);
		}
		for (j = 1, i = imgInfo.bytesPixel + 1; i <= nbytes; i++, j++) {
			rowUnfiltered[i] = (byte) (row[i] + rowUnfiltered[j]);
		}
	}

	private void unfilterRowUp(final int nbytes) {
		for (int i = 1; i <= nbytes; i++) {
			rowUnfiltered[i] = (byte) (row[i] + rowUnfilteredPrev[i]);
		}
	}

	/**
	 * does the unfiltering of the inflated row, and updates row info
	 */
	@Override
	protected void preProcessRow() {
		super.preProcessRow();
		rowinfo.update(getRown());
		unfilterRow();
		rowinfo.updateBuf(rowUnfiltered, rowinfo.bytesRow + 1);
	}

	/**
	 * Method for async/callback mode .
	 * <p>
	 * In callback mode will be called as soon as each row is retrieved
	 * (inflated and unfiltered), after {@link #preProcessRow()}
	 * <p>
	 * This is a dummy implementation that does nothing.
	 * <p>
	 * The return value is essential
	 * <p>
	 * 
	 * @return Length of next row, in bytes (including filter byte),
	 *         non-positive if done
	 */
	@Override
	protected int processRowCallback() {
		int bytesNextRow = advanceToNextRow();
		return bytesNextRow;
	}

	/**
	 * Signals that we are done with the previous row, begin reading the next
	 * one.
	 * <p>
	 * In polled mode, calls setNextRowLen()
	 * <p>
	 * Warning: after calling this, the unfilterRow is invalid!
	 * 
	 * @return Returns nextRowLen
	 */
	public int advanceToNextRow() {
		//PngHelperInternal.LOGGER.info("advanceToNextRow");
		int bytesNextRow;
		if (deinterlacer == null) {
			bytesNextRow = getRown() >= imgInfo.rows - 1 ? 0 : imgInfo.bytesPerRow + 1;
		} else {
			boolean more = deinterlacer.nextRow();
			bytesNextRow = more ? deinterlacer.getBytesToRead() + 1 : 0;
		}
		if (!isCallbackMode()) // in callback mode, setNextRowLen() is called internally
			prepareForNextRow(bytesNextRow);
		return bytesNextRow;
	}

	public boolean isRowReady() {
		return !isWaitingForMoreInput();

	}

	/**
	 * Unfiltered row.
	 * <p>
	 * This should be called only if {@link #isRowReady()} returns true.
	 * <p>
	 * To get real length, use {@link #getRowLen()}
	 * <p>
	 * 
	 * @return Unfiltered row, includes filter byte
	 */
	public byte[] getUnfilteredRow() {
		return rowUnfiltered;
	}

	public Deinterlacer getDeinterlacer() {
		return deinterlacer;
	}

	void updateCrc(CRC32 idatCrc) {
		if (idatCrc != null)// just for testing
			idatCrc.update(getUnfilteredRow(), 1, getRowFilled() - 1);
	}

}