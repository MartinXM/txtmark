/*
* Copyright (C) 2011 René Jeschke <rene_jeschke@yahoo.de>
* See LICENSE.txt for licensing information.
*/
package txtmark;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;

/**
 * Markdown processor class.
 * 
 * @author René Jeschke <rene_jeschke@yahoo.de>
 */
public class Processor
{
    /** The reader. */
    private final Reader reader;
    /** The emitter. */
    private Emitter emitter = new Emitter();

    /**
     * Constructor.
     * 
     * @param reader The input reader.
     */
    private Processor(Reader reader)
    {
        this.reader = reader;
    }

    /**
     * Transforms an input String into XHTML.
     * 
     * @param input The String to process. 
     * @return The processed String.
     * @throws IOException if an IO error occurs
     */
    public static String process(final String input) throws IOException
    {
        return process(new StringReader(input));
    }

    /**
     * Transforms an input file into XHTML using UTF-8 encoding.
     * 
     * @param file The File to process. 
     * @return The processed String.
     * @throws IOException if an IO error occurs
     */
    public static String process(final File file) throws IOException
    {
        return process(file, "UTF-8");
    }

    /**
     * Transforms an input file into XHTML.
     * 
     * @param file The File to process. 
     * @param encoding The encoding to use. 
     * @return The processed String.
     * @throws IOException if an IO error occurs
     */
    public static String process(final File file, final String encoding) throws IOException
    {
        final Reader r = new BufferedReader(new InputStreamReader(new FileInputStream(file), encoding));
        final Processor p = new Processor(r);
        final String ret = p.process();
        r.close();
        return ret;
    }

    /**
     * Transforms an input stream into XHTML using UTF-8 encoding.
     * 
     * @param input The InputStream to process. 
     * @return The processed String.
     * @throws IOException if an IO error occurs
     */
    public static String process(final InputStream input) throws IOException
    {
        return process(input, "UTF-8");
    }

    /**
     * Transforms an input stream into XHTML.
     * 
     * @param input The InputStream to process. 
     * @param encoding The encoding to use. 
     * @return The processed String.
     * @throws IOException if an IO error occurs
     */
    public static String process(final InputStream input, final String encoding) throws IOException
    {
        final Processor p = new Processor(new BufferedReader(new InputStreamReader(input, encoding)));
        return p.process();
    }

    /**
     * Transforms an input stream into XHTML.
     * 
     * @param reader The Reader to process. 
     * @return The processed String.
     * @throws IOException if an IO error occurs
     */
    public static String process(final Reader reader) throws IOException
    {
        final Processor p = new Processor(!(reader instanceof BufferedReader) ? new BufferedReader(reader) : reader);
        return p.process();
    }

    /**
     * Reads all lines from our reader.
     * <p>Takes care of markdown link references.</p>
     * 
     * @return A Block containing all lines.
     * @throws IOException If an IO error occurred.
     */
    private Block readLines() throws IOException
    {
        final Block block = new Block();
        final StringBuilder sb = new StringBuilder(80);
        int c = this.reader.read();
        LinkRef lastLinkRef = null;
        while(c != -1)
        {
            sb.setLength(0);
            int pos = 0;
            boolean eol = false;
            while(!eol)
            {
                switch(c)
                {
                case -1:
                    eol = true;
                    break;
                case '\n':
                    c = this.reader.read();
                    if(c == '\r')
                        c = this.reader.read();
                    eol = true;
                    break;
                case '\r':
                    c = this.reader.read();
                    if(c == '\n')
                        c = this.reader.read();
                    eol = true;
                    break;
                case '\t':
                    {
                        final int np = pos + (4 - (pos & 3));
                        while(pos < np)
                        {
                            sb.append(' ');
                            pos++;
                        }
                        c = this.reader.read();
                    }
                    break;
                default:
                    pos++;
                    sb.append((char)c);
                    c = this.reader.read();
                    break;
                }
            }

            final Line line = new Line();
            line.value = sb.toString();
            line.init();

            // Check for link definitions
            boolean isLinkRef = false;
            String id = null, link = null, comment = null;
            if(!line.isEmpty && line.leading < 4 && line.value.charAt(line.leading) == '[')
            {
                line.pos = line.leading + 1;
                // Read ID up to ']'
                id = line.readUntil(']');
                // Is ID valid and are there any more characters?
                if(id != null && line.pos + 2 < line.value.length())
                {
                    // Check for ':' ([...]:...)
                    if(line.value.charAt(line.pos + 1) == ':')
                    {
                        line.pos += 2;
                        line.skipSpaces();
                        // Check for link syntax
                        if(line.value.charAt(line.pos) == '<')
                        {
                            line.pos++;
                            link = line.readUntil('>');
                            line.pos++;
                        }
                        else
                            link = line.readUntil(' ', '\n');

                        // Is link valid?
                        if(link != null)
                        {
                            // Any non-whitespace characters following?
                            if(line.skipSpaces())
                            {
                                final char ch = line.value.charAt(line.pos);
                                // Read comment
                                if(ch == '\"' || ch == '\'' || ch == '(')
                                {
                                    line.pos++;
                                    comment = line.readUntil(ch == '(' ? ')' : ch);
                                    // Valid linkRef only if comment is valid
                                    if(comment != null)
                                        isLinkRef = true;
                                }
                            }
                            else
                                isLinkRef = true;
                        }
                    }
                }
            }

            if(isLinkRef)
            {
                // Store linkRef and skip line
                final LinkRef lr = new LinkRef(link, comment);
                this.emitter.addLinkRef(id, lr);
                if(comment == null)
                    lastLinkRef = lr;
            }
            else
            {
                comment = null;
                // Check for multi-line linkRef
                if(!line.isEmpty && lastLinkRef != null)
                {
                    line.pos = line.leading;
                    final char ch = line.value.charAt(line.pos);
                    if(ch == '\"' || ch == '\'' || ch == '(')
                    {
                        line.pos++;
                        comment = line.readUntil(ch == '(' ? ')' : ch);
                    }
                    if(comment != null)
                        lastLinkRef.title = comment;

                    lastLinkRef = null;
                }

                // No multi-line linkRef, store line
                if(comment == null)
                {
                    line.pos = 0;
                    block.appendLine(line);
                }
            }
        }

        return block;
    }

    /**
     * Initializes a list block by separating it into list item blocks.
     * 
     * @param root The Block to process.
     */
    private void initListBlock(final Block root)
    {
        Line line = root.lines;
        line = line.next;
        while(line != null)
        {
            final LineType t = line.getLineType();
            if(
                    (t == LineType.OLIST || t == LineType.ULIST) ||
                    (!line.isEmpty && (line.prevEmpty && line.leading == 0 && !(t == LineType.OLIST || t == LineType.ULIST))))
            {
                root.split(line.previous).type = BlockType.LIST_ITEM;
            }
            line = line.next;
        }
        root.split(root.lineTail).type = BlockType.LIST_ITEM;
    }

    /**
     * Recursively process the given Block.
     * 
     * @param root The Block to process.
     * @param listMode Flag indicating that we're in a list item block.
     */
    private void recurse(final Block root, boolean listMode)
    {
        Block block;
        Line line = root.lines;
        while(line != null && line.isEmpty) line = line.next;
        if(line == null)
            return;

        if(listMode)
            root.removeListIndent();

        boolean hasParagraph = false;

        while(line != null)
        {
            final LineType type = line.getLineType();
            switch(type)
            {
            case OTHER:
                {
                    final boolean wasEmpty = line.prevEmpty;
                    while(line != null && !line.isEmpty)
                    {
                        final LineType t = line.getLineType();
                        if(listMode && (t == LineType.OLIST || t == LineType.ULIST))
                            break;
                        if(t == LineType.HEADLINE || t == LineType.HEADLINE1 || t == LineType.HEADLINE2 || t == LineType.HR || t == LineType.BQUOTE)
                            break;
                        line = line.next;
                    }
                    final BlockType bt;
                    if(line != null && !line.isEmpty)
                    {
                        bt = (listMode && root.blocks == null && !wasEmpty) ? BlockType.NONE : BlockType.PARAGRAPH;
                        root.split(line.previous).type = bt;
                        root.removeLeadingEmptyLines();
                    }
                    else
                    {
                        bt = (listMode && (line == null || !line.isEmpty) && !wasEmpty) ? BlockType.NONE : BlockType.PARAGRAPH;
                        root.split(line == null ? root.lineTail : line).type = bt;
                        root.removeLeadingEmptyLines();
                    }
                    hasParagraph = bt == BlockType.PARAGRAPH;
                    line = root.lines;
                }
                break;
            case CODE:
                while(line != null && (line.isEmpty || line.leading > 3))
                {
                    line = line.next;
                }
                block = root.split(line != null ? line.previous : root.lineTail);
                block.type = BlockType.CODE;
                block.removeSurroundingEmptyLines();
                break;
            case BQUOTE:
                while(line != null)
                {
                    if(!line.isEmpty && (line.prevEmpty && line.leading == 0 && line.getLineType() != LineType.BQUOTE))
                        break;
                    line = line.next;
                }
                block = root.split(line != null ? line.previous : root.lineTail);
                block.type = BlockType.BLOCKQUOTE;
                block.removeSurroundingEmptyLines();
                block.removeBlockQuotePrefix();
                this.recurse(block, false);
                line = root.lines;
                break;
            case HR:
                if(line.previous != null)
                {
                    root.split(line.previous);
                }
                root.split(line).type = BlockType.RULER;
                root.removeLeadingEmptyLines();
                line = root.lines;
                break;
            case HEADLINE:
            case HEADLINE1:
            case HEADLINE2:
                if(line.previous != null)
                {
                    root.split(line.previous);
                }
                if(type != LineType.HEADLINE)
                {
                    line.next.setEmpty();
                }
                block = root.split(line);
                block.type = BlockType.HEADLINE;
                if(type != LineType.HEADLINE)
                block.hlDepth = type == LineType.HEADLINE1 ? 1 : 2;
                block.transfromHeadline();
                root.removeLeadingEmptyLines();
                line = root.lines;
                break;
            case OLIST:
            case ULIST:
                while(line != null)
                {
                    final LineType t = line.getLineType();
                    if(!line.isEmpty && (line.prevEmpty && line.leading == 0 && !(t == LineType.OLIST || t == LineType.ULIST)))
                        break;
                    line = line.next;
                }
                block = root.split(line != null ? line.previous : root.lineTail);
                block.type = type == LineType.OLIST ? BlockType.ORDERED_LIST : BlockType.UNORDERED_LIST;
                block.lines.prevEmpty = false;
                block.lineTail.nextEmpty = false;
                block.removeSurroundingEmptyLines();
                block.lines.prevEmpty = block.lineTail.nextEmpty = false;
                this.initListBlock(block);
                block = block.blocks;
                while(block != null)
                {
                    this.recurse(block, true);
                    block = block.next;
                }
                break;
            default:
                line = line.next;
                break;
            }
        }

        if(listMode && hasParagraph)
        {
            block = root.blocks;
            while(block != null)
            {
                if(block.type == BlockType.NONE)
                    block.type = BlockType.PARAGRAPH;
                block = block.next;
            }
        }
    }

    /**
     * Does all the processing.
     * 
     * @return The processed String.
     * @throws IOException If an IO error occurred.
     */
    private String process() throws IOException
    {
        final StringBuilder out = new StringBuilder();

//        long t0 = System.nanoTime();

        final Block parent = this.readLines();
        parent.removeSurroundingEmptyLines();

        this.recurse(parent, false);
        Block block = parent.blocks;
        while(block != null)
        {
            this.emitter.emit(out, block);
            block = block.next;
        }

//        t0 = System.nanoTime() - t0;
//        out.append(String.format("\n<!-- Processing time: %dms -->\n", (int)(t0 * 1e-6)));

        return out.toString();
    }
}
