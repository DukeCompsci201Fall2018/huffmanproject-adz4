import java.util.*;

/**
 * Although this class has a history of several years,
 * it is starting from a blank-slate, new and clean implementation
 * as of Fall 2018.
 * <P>
 * Changes include relying solely on a tree for header information
 * and including debug and bits read/written information
 * 
 * @author Owen Astrachan
 */

public class HuffProcessor {

	public static final int BITS_PER_WORD = 8;
	public static final int BITS_PER_INT = 32;
	public static final int ALPH_SIZE = (1 << BITS_PER_WORD); 
	public static final int PSEUDO_EOF = ALPH_SIZE;
	public static final int HUFF_NUMBER = 0xface8200;
	public static final int HUFF_TREE  = HUFF_NUMBER | 1;

	private final int myDebugLevel;
	
	public static final int DEBUG_HIGH = 4;
	public static final int DEBUG_LOW = 1;
	
	public HuffProcessor() {
		this(0);
	}
	
	public HuffProcessor(int debug) {
		myDebugLevel = debug;
	}

	/**
	 * Compresses a file. Process must be reversible and loss-less.
	 *
	 * @param in
	 *            Buffered bit stream of the file to be compressed.
	 * @param out
	 *            Buffered bit stream writing to the output file.
	 */
	public void compress(BitInputStream in, BitOutputStream out){

		int[] counts = readForCounts(in);
		HuffNode root = makeTreeFromCounts(counts);
		String[] codings = makeCodingsFromTree(root);
		
		out.writeBits(BITS_PER_INT, HUFF_TREE);
		writeHeader(root,out);
		
		in.reset();
		writeCompressedBits(codings,in,out);
		out.close();
	}
	
	/** Write the encoding for each eight-bit chunk, followed by 
	 * the encoding for PSEUDO_EOF.
	 * @param codings - String[] with all 8-bit chunks
	 * @param in - bit stream of the file being compressed
	 * @param out - bit stream writing to the output file
	 */
	private void writeCompressedBits(String[] codings, BitInputStream in, BitOutputStream out) {
		while (true) {
			int bits = in.readBits(BITS_PER_WORD);
			if (bits == -1) break;
			String code = codings[bits];
			out.writeBits(code.length(), Integer.parseInt(code,2));
		}
		
		String code = codings[PSEUDO_EOF];
		out.writeBits(code.length(), Integer.parseInt(code,2));
		
	}

	/** Write the magic number and the tree to the beginning/header 
	 * of the compressed file
	 * @param root - root of the Huffman Trie used for header
	 * @param out - bits stream writing to the output file
	 */
	private void writeHeader(HuffNode root, BitOutputStream out) {
		if (root.myLeft == null && root.myRight == null) {
			out.writeBits(1, 1);
			out.writeBits(BITS_PER_WORD + 1, root.myValue);
			return;
		}
		
		out.writeBits(1, 0);
		writeHeader(root.myLeft, out);
		writeHeader(root.myRight, out);
	}

	/** Using a trie, create encodings for each 8-bit char/chunk
	 * @param root - root of the Huffman Trie
	 * @return a String[] w/ encodings
	 */
	private String[] makeCodingsFromTree(HuffNode root) {
		String[] encodings = new String[ALPH_SIZE + 1];
		codingHelper(root,"",encodings);
		return encodings;
	}

	/** Helper method used with for makeCodingsFromTree using
	 * recursive methods to encode, like doWork in LeafTrails APT.
	 * @param root - root of Huffman Trie
	 * @param string - string to build encoding
	 * @param encodings - String[] of encodings
	 */
	private void codingHelper(HuffNode root, String path, String[] encodings) {
		if (root.myLeft == null && root.myRight == null) {
			encodings[root.myValue] = path;
			if (myDebugLevel >= DEBUG_HIGH) {
				System.out.printf("encoding for %d is %s\n",
						root.myValue,path);
			}
			return;
		}
		
		codingHelper(root.myLeft, path + "0", encodings);
		codingHelper(root.myRight, path + "1", encodings);
	}

	/** Create Huffman Tree for encoding, using frequencies
	 * @param counts - int[] of frequencies for 8-bit chars/chunks
	 * @return a Huffman Tree
	 */
	private HuffNode makeTreeFromCounts(int[] counts) {
		PriorityQueue<HuffNode> pq = new PriorityQueue<>();
		
		for(int i=0; i<counts.length; i++) {
			if (counts[i] > 0) {
				pq.add(new HuffNode(i, counts[i], null, null));
			}
		}
		
		while(pq.size() > 1) {
			HuffNode left = pq.remove();
			HuffNode right = pq.remove();
			int weight = left.myWeight + right.myWeight;
			HuffNode t = new HuffNode(0, weight, left, right);
			pq.add(t);
		}
		
		HuffNode root = pq.remove();
		return root;
	}

	/** Create an int[] that will store the frequencies of
	 * every 8-bit character/chunk in the file to be compressed
	 * @param in - bit stream of the file to be compressed
	 * @return an int array of frequencies for every 8-bit char
	 */
	private int[] readForCounts(BitInputStream in) {
		int[] freq = new int[ALPH_SIZE + 1];
		freq[PSEUDO_EOF] = 1;
		
		while (true) {
			int bit = in.readBits(BITS_PER_WORD);
			if (bit == -1) break;
			freq[bit] += 1;
		}
		return freq;
	}

	/**
	 * Decompresses a file. Output file must be identical bit-by-bit to the
	 * original.
	 *
	 * @param in
	 *            Buffered bit stream of the file to be decompressed.
	 * @param out
	 *            Buffered bit stream writing to the output file.
	 */
	public void decompress(BitInputStream in, BitOutputStream out){

		int bits = in.readBits(BITS_PER_INT);
		if (bits != HUFF_TREE) {
			throw new HuffException("illegal header starts with " + bits);
		}
		
		HuffNode root = readTreeHeader(in);
		readCompressedBits(root,in,out);
		out.close();
	}

	/** Read the bits from BitInputStream one at a time
	 * traversing through tree from the root going left or right
	 * depending on if you read a 0 or a 1
	 * @param root - the root of the HuffTree
	 * @param in - the inputed bit stream representing file
	 * @param out - the bit stream writing to the output file
	 */
	private void readCompressedBits(HuffNode root, BitInputStream in, BitOutputStream out) {
		HuffNode current = root;
		while (true) {
			int bits = in.readBits(1);
			if (bits == -1) {
				throw new HuffException("bad input, no PSEUDO_EOF");
			}
			else {
				if (bits == 0) current = current.myLeft;
				else current = current.myRight;
				
				if (current.myLeft == null && current.myRight == null) {
					if (current.myValue == PSEUDO_EOF) break;
					else {
						out.writeBits(BITS_PER_WORD, current.myValue);
						current = root;
					}
				}
			}
		}
	}

	/** Reads the tree used to decompress the file,
	 * stored using pre-order traversal, uses recursion
	 * @param in - the inputed bit stream representing file
	 * @return a HuffTree to be used to read compressed bits
	 */
	private HuffNode readTreeHeader(BitInputStream in) {
		int bit = in.readBits(1);
		if (bit == -1) {
			throw new HuffException("bad input, no PSEUDO_EOF");
		}
		
		if (bit == 0) {
			HuffNode left = readTreeHeader(in);
			HuffNode right = readTreeHeader(in);
			return new HuffNode(0,0,left,right);
		}
		else {
			int value = in.readBits(BITS_PER_WORD + 1);
			return new HuffNode(value,0,null,null);
		}
	}
}