package tk.donkeyblaster;

public class Main {
    public static void main(String[] args) {
        Thread btcThread = new Thread(() -> {
            SingularPriceBot BTC = new SingularPriceBot("BTC");
        });
        Thread ethThread = new Thread(() -> {
            SingularPriceBot ETH = new SingularPriceBot("ETH");
        });
        Thread solThread = new Thread(() -> {
            SingularPriceBot SOL = new SingularPriceBot("SOL");
        });
        Thread ltcThread = new Thread(() -> {
            SingularPriceBot LTC = new SingularPriceBot("LTC");
        });

        btcThread.start();
        ethThread.start();
        solThread.start();
        ltcThread.start();
    }
}