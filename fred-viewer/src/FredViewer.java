import javax.swing.*;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.image.BufferedImage;
import java.awt.image.ImageObserver;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;

public class FredViewer {

    public static void main(String[] args) {
        if (args.length != 1) {
            System.out.println("Please supply a path to snapshot as a parameter");
            return;
        }
        FredViewer viewer = new FredViewer(args[0]);
    }

    public FredViewer(String snapshotFile) {
        JFrame frame = new JFrame("Fred viewer");

        final byte[] zxMem;
        try {
            zxMem = loadSna(snapshotFile);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        frame.setSize(new Dimension(500, 300));

        JTabbedPane tabPane = new JTabbedPane();

        {

            int blockIdx = 0;
            byte[][] blockBytes = getBlock(zxMem, blockIdx);

            final SpriteView blockView = new SpriteView(blockBytes);

            JPanel panel = new JPanel(new BorderLayout());

            JScrollPane scrollPane = new JScrollPane(blockView);
            panel.add(scrollPane, BorderLayout.CENTER);

            JPanel controlPanel = new JPanel();
            JComboBox comboBox = new JComboBox(new DefaultComboBoxModel(new Integer[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 9}));
            comboBox.addItemListener(new ItemListener() {
                @Override
                public void itemStateChanged(ItemEvent e) {
                    if (e.getStateChange() == ItemEvent.SELECTED) {
                        int blockIdx = ((Integer) e.getItem()).intValue();
                        blockView.setUnpacked(getBlock(zxMem, blockIdx));
                        blockView.repaint();
                    }
                }
            });
            controlPanel.add(comboBox);
            panel.add(controlPanel, BorderLayout.NORTH);

            tabPane.addTab("Blocks", panel);
        }

        {
            byte[][] map = getMap(zxMem);
            final MapView mapView = new MapView(map);
            JScrollPane scrollPane = new JScrollPane(mapView);
            tabPane.addTab("Map", scrollPane);
        }

        {
            byte[][] map = getMap(zxMem);
            java.util.List<byte[][]> blocks = new ArrayList<>();
            for (int i = 0; i < 8; i++) {
                blocks.add(getBlock(zxMem, i));
            }
            final DetailMapView mapView = new DetailMapView(map, blocks);
            JScrollPane scrollPane = new JScrollPane(mapView);
            tabPane.addTab("Detail map", scrollPane);
        }

        tabPane.addTab("Hedgehogs", createSpriteSetPanel(zxMem, 0x6140, 0, 2, 1));

        tabPane.addTab("Droplets", createSpriteSetPanel(zxMem, 0x8148, 0, 2, 1));

        tabPane.addTab("Player", createSpriteSetPanel(zxMem, 0xc000, 10, 4, 4));

        tabPane.addTab("Skeleton", createSpriteSetPanel(zxMem, 0xE202, 0, 4, 4));

        tabPane.addTab("Bat", createSpriteSetPanel(zxMem, 0x61DA, 0, 3, 2));

        tabPane.addTab("Mummy", createSpriteSetPanel(zxMem, 0xE186, 0, 3, 4));

        tabPane.addTab("Lizard", createSpriteSetPanel(zxMem, 0x81F2, 0, 1, 2));

        tabPane.addTab("Ghost", createSpriteSetPanel(zxMem, 0xE156, 0, 3, 4));


        // rope
        // byte[][] spriteBytes = getSprite(zxMem, 0x6518, 4, 4);

        // hedgehog right
        // byte[][] spriteBytes = getSprite(zxMem, 0xbff2, 2, 1);

        // piece of rope
        // byte[][] spriteBytes = getSprite(zxMem, 0xc1fc, 2, 1);

        // piece of rope
        // byte[][] spriteBytes = getSprite(zxMem, 0xf0aa, 2, 1);

        // ??
        // byte[][] spriteBytes = getSprite(zxMem, 0xf058, 2, 1);
        // byte[][] spriteBytes = getSprite(zxMem, 0xeeb8, 3, 4);



        frame.getContentPane().add(tabPane, BorderLayout.CENTER);

        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        frame.setVisible(true);
    }

    private static JPanel createSpriteSetPanel(byte[] zxMem, final int spriteBase, final int spriteBaseCode, int w, int h) {
        // player's sprite is 4*4, so index multiplier is 16
        int startCellCode = spriteBase + (0 + spriteBaseCode) * w * h;
        byte[][] spriteBytes = getSpriteSeq(zxMem, startCellCode, w, h);

        final SpriteView spriteView = new SpriteView(spriteBytes);

        JPanel panel = new JPanel(new BorderLayout());

        JScrollPane scrollPane = new JScrollPane(spriteView);
        panel.add(scrollPane, BorderLayout.CENTER);

        JPanel controlPanel = new JPanel();
        controlPanel.add(new JLabel("Sprite code:"));
        JComboBox comboBox = new JComboBox(new DefaultComboBoxModel(new Integer[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 9}));
        controlPanel.add(comboBox);
        controlPanel.add(new JLabel("Cell code:"));
        JTextField startCellCodeText = new JTextField(Integer.toHexString(startCellCode));
        controlPanel.add(startCellCodeText);
        comboBox.addItemListener(new ItemListener() {
            @Override
            public void itemStateChanged(ItemEvent e) {
                if (e.getStateChange() == ItemEvent.SELECTED) {
                    int blockIdx = ((Integer) e.getItem()).intValue();
                    int startCellCode1 = spriteBase + (blockIdx + spriteBaseCode) * w * h;
                    startCellCodeText.setText(Integer.toHexString(startCellCode1));
                    byte[][] spriteSeq = getSpriteSeq(zxMem, startCellCode1, w, h);
                    spriteView.setUnpacked(spriteSeq);
                    spriteView.repaint();
                }
            }
        });
        panel.add(controlPanel, BorderLayout.NORTH);
        return panel;
    }

    private static byte[][] getBlock(byte[] zxMem, int blockIdx) {
        byte[][] result = new byte[8*5][];
        int cellPtr = 0x64d3 + (blockIdx*2);
        int cellCode = (0xff & zxMem[cellPtr]) + 256*(0xff & zxMem[cellPtr + 1]);
        for (int j = 0; j < 5; j++) {
            for (int r = 0; r < 8; r ++) {
                result[j*8 + r] = new byte[8 * 4];
            }
            for (int k = 0; k < 4; k++) {
                writeSprite(zxMem, result, j, k, cellCode);

                cellCode = cellCode + 1;
            }
        }
        return result;
    }

    private static void writeSprite(byte[] zxMem, byte[][] result, int j, int k, int cellCode) {
        int cellAddr = getCellAddr(cellCode);

        byte[] cellContent = new byte[8];
        for (int i = 0; i < 8; i++) {
            cellContent[i] = zxMem[cellAddr + i];
            result[j *8 + i][k] = zxMem[cellAddr + i];
        }
    }

    private static byte[][] getSpriteSeq(byte[] zxMem, int startCellCode, int w, int h) {
        byte[][] result = new byte[8*h][];
        int cellCode = startCellCode;
        for (int j = 0; j < h; j++) {
            for (int r = 0; r < 8; r ++) {
                result[j*8 + r] = new byte[8 * w];
            }
            for (int k = 0; k < w; k++) {
                writeSprite(zxMem, result, j, k, cellCode);
                cellCode = cellCode + 1;
            }
        }
        return result;
    }


    private static int getCellAddr(int cellCode) {
        int cellAddrOffset = (0xfff & cellCode) * 8;
        int cellAddr = 0x9400 + cellAddrOffset;
        return cellAddr;
    }

    private static byte[][] getMap(byte[] zxMem) {
        byte[][] result = new byte[32][];
        int current = 0xE000;
        for (int i = 0; i < result.length; i++) {
            byte[] row = new byte[32];
            result[i] = row;
            for (int j = 0; j < row.length; j++) {
                byte b = zxMem[current++];
                row[j] = b; // (byte)(b > 3 ? 0 : 1);
            }
        }
        return result;
    }

    private static void paintSprite(Graphics g, int scale, Color main, byte[][] data, int x, int y) {
        for (int i = 0; i < data.length; i++) {
            byte[] row = data[i];
            for (int j = 0; j < row.length; j++) {
                for (int k = 0; k < 8; k++) {
                    int bit = row[j] & (1 << (7 - k));
                    if (bit == 0) {
                        g.setColor(Color.BLACK);
                    } else {
                        g.setColor(main);
                    }
                    g.fillRect(x + (j*8 + k)* scale, y + i* scale, scale, scale);
                }
            }
        }
    }

    private static class SpriteView extends JPanel {

        private int scale = 10;

        private byte[][] data;

        public SpriteView(byte[][] data) {
            this.data = data;
        }

        private void setUnpacked(byte[][] data) {
            this.data = data;
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            paintSprite(g, scale, Color.WHITE, data, 0, 0);
        }

    }

    private static class MapView extends JPanel {

        private int scale = 10;

        private byte[][] mapData;

        public MapView(byte[][] mapData) {
            this.mapData = mapData;
        }

        private void setUnpacked(byte[][] data) {
            this.mapData = data;
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            for (int i = 0; i < mapData.length; i++) {
                byte[] row = mapData[i];
                for (int j = 0; j < row.length; j++) {
                    int b = row[j];
                    if ((b >= 4 ) || (b == 0)) {
                        g.setColor(Color.BLACK);
                    } else {
                        g.setColor(Color.WHITE);
                    }
                    g.fillRect(j*scale, i*scale, scale, scale);
                }
            }
        }
    }

    private static class DetailMapView extends JPanel {

        private int scale = 2;

        private byte[][] mapData;
        java.util.List<byte[][]> blockSprites;
        java.util.List<BufferedImage> blockSpriteImgs;

        public DetailMapView(byte[][] data, java.util.List<byte[][]> blockSprites) {
            this.mapData = data;
            this.blockSprites = blockSprites;
            blockSpriteImgs = new ArrayList<>();
            for (byte[][] blockSprite : blockSprites) {
                BufferedImage bi = new BufferedImage(scale * blockSprite[0].length, scale * blockSprite.length, BufferedImage.TYPE_INT_RGB);
                Graphics2D g = bi.createGraphics();
                paintSprite(g, scale, Color.CYAN, blockSprite, 0, 0);
                blockSpriteImgs.add(bi);
            }
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            for (int i = 0; i < mapData.length; i++) {
                byte[] row = mapData[i];
                for (int j = 0; j < row.length; j++) {
                    int b = row[j];
                    if (b == 0) {
                        g.setColor(Color.BLACK);
                        g.fillRect(j*4*8*scale, i*5*8*scale, 4*8*scale, 5*8*scale);
                    } else {
                        g.drawImage(blockSpriteImgs.get(b), j * 4 * 8 * scale, i * 5 * 8 * scale, new ImageObserver() {
                            @Override
                            public boolean imageUpdate(Image img, int infoflags, int x, int y, int width, int height) {
                                return false;
                            }
                        });
                    }
                }
            }

        }
    }


    private static byte[] loadSna(String filename) throws Exception {
        var file = new File(filename);
        InputStream in = new BufferedInputStream(new FileInputStream(file));
        byte[] snaData = new byte[50000];
        int result = in.read(snaData);
        byte[] zxMem = new byte[65536];
        System.arraycopy(snaData, 27, zxMem, 16384, 49152 );

        return zxMem;
    }

}
