package com.zlorbie788.zeus.core;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.zlorbie788.zeus.ui.KnifeHD;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Zeus Crates: the knives you own, the crates and keys you hold, Zeus tokens earned by playing,
 * and promo codes. Everything is kept in config/zeus/crates.dat, signed so a hand-edited file is
 * noticed and reset rather than trusted.
 */
public final class Crates {
    private Crates() {
    }

    // ------------------------------------------------------------------ rarities
    public enum Rarity {
        MORTAL("Mortal", 0xFF4B69FF, 6000, 1),
        HEROIC("Heroic", 0xFF8847FF, 2500, 2),
        DIVINE("Divine", 0xFFD32CE6, 1000, 4),
        TITAN("Titan", 0xFFEB4B4B, 400, 8),
        OLYMPIAN("Olympian", 0xFFFFC83A, 100, 20);

        public final String label;
        public final int colour;
        /** Weight out of 10 000. */
        public final int weight;
        /** Tokens given back when the knife is already owned. */
        public final int refund;

        Rarity(String label, int colour, int weight, int refund) {
            this.label = label;
            this.colour = colour;
            this.weight = weight;
            this.refund = refund;
        }

        public String odds() {
            return weight % 100 == 0 ? (weight / 100) + "%" : String.format(Locale.ROOT, "%.1f%%", weight / 100f);
        }
    }

    public record Item(String id, Rarity rarity) {
    }

    public static final class Crate {
        public final String id;
        public final String name;
        public final String tagline;
        public final int colour;
        public final int dark;
        public final List<Item> items = new ArrayList<>();

        Crate(String id, String name, String tagline, int colour, int dark) {
            this.id = id;
            this.name = name;
            this.tagline = tagline;
            this.colour = colour;
            this.dark = dark;
        }

        Crate add(Rarity r, String... ids) {
            for (String s : ids) items.add(new Item(s, r));
            return this;
        }

        public Item flagship() {
            for (Item it : items) if (it.rarity == Rarity.OLYMPIAN) return it;
            return items.get(0);
        }
    }

    public static final String STARTER = "karambit_stock";
    public static final int KEY_PRICE = 30;
    public static final long TOKEN_MS = 5 * 60_000L;
    public static final long DROP_MS = 90 * 60_000L;
    private static final long IDLE_MS = 90_000L;

    public static final List<Crate> CRATES = List.of(
            new Crate("olympus", "Olympus Crate", "Marble, gold and the forge of the gods", 0xFFF5C04A, 0xFF3A2A08)
                    .add(Rarity.OLYMPIAN, "bowie_olympus")
                    .add(Rarity.TITAN, "tac_olympus", "ursus_gold")
                    .add(Rarity.DIVINE, "tac_damascus", "m9_damascus", "skeleton_case")
                    .add(Rarity.HEROIC, "tac_tiger", "bowie_damascus", "tac_case")
                    .add(Rarity.MORTAL, "huntsman_case", "stiletto_damascus", "stiletto_case"),
            new Crate("tempest", "Tempest Crate", "Storm steel, charged with lightning", 0xFF5AA8FF, 0xFF0A1A38)
                    .add(Rarity.OLYMPIAN, "tac_lightning")
                    .add(Rarity.TITAN, "huntsman_storm", "karambit_sapphire")
                    .add(Rarity.DIVINE, "tac_doppler", "bowie_sapphire", "ursus_sapphire")
                    .add(Rarity.HEROIC, "tac_urban", "ursus_urban", "skeleton_night")
                    .add(Rarity.MORTAL, "m9_night", "huntsman_night", "stiletto_night"),
            new Crate("hades", "Hades Crate", "Forged in the underworld's fire", 0xFFFF4A4A, 0xFF3A0808)
                    .add(Rarity.OLYMPIAN, "karambit_bloodstone")
                    .add(Rarity.TITAN, "karambit_inferno", "bowie_inferno")
                    .add(Rarity.DIVINE, "karambit_crimson", "tac_ruby", "m9_ruby")
                    .add(Rarity.HEROIC, "tac_crimson", "karambit_slaughter", "huntsman_crimson")
                    .add(Rarity.MORTAL, "tac_slaughter", "butterfly_slaughter", "bowie_slaughter"),
            new Crate("nyx", "Nyx Crate", "Night, nebulae and things that shimmer", 0xFFB06AFF, 0xFF1E0A3A)
                    .add(Rarity.OLYMPIAN, "karambit_nebula")
                    .add(Rarity.TITAN, "m9_phantom", "ursus_nebula")
                    .add(Rarity.DIVINE, "skeleton_phantom", "butterfly_ultraviolet", "karambit_fade")
                    .add(Rarity.HEROIC, "tac_ultraviolet", "butterfly_fade", "bowie_fade")
                    .add(Rarity.MORTAL, "tac_fade", "skeleton_fade", "stiletto_fade"),
            new Crate("poseidon", "Poseidon Crate", "Pulled up from the deep", 0xFF2AD0D0, 0xFF052A30)
                    .add(Rarity.OLYMPIAN, "skeleton_abyss")
                    .add(Rarity.TITAN, "m9_frost", "ursus_frost")
                    .add(Rarity.DIVINE, "stiletto_abyss", "tac_emerald", "butterfly_emerald")
                    .add(Rarity.HEROIC, "skeleton_emerald", "huntsman_urban", "huntsman_tiger")
                    .add(Rarity.MORTAL, "m9_camo", "m9_case"),
            new Crate("titan", "Titan Crate", "Loud colours for those who want to be seen", 0xFFFF8A2A, 0xFF3A1A04)
                    .add(Rarity.OLYMPIAN, "butterfly_sunset")
                    .add(Rarity.TITAN, "butterfly_toxic", "karambit_tiger")
                    .add(Rarity.DIVINE, "butterfly_marble", "stiletto_sunset", "tac_marble")
                    .add(Rarity.HEROIC, "huntsman_toxic", "ursus_tiger", "skeleton_marble")
                    .add(Rarity.MORTAL, "stiletto_marble", "bowie_rust", "ursus_ruby"));

    public static Crate crate(String id) {
        for (Crate c : CRATES) if (c.id.equals(id)) return c;
        return null;
    }

    /** Which crate a knife drops from, or null for the starter. */
    public static Crate crateOf(String knifeId) {
        for (Crate c : CRATES) for (Item it : c.items) if (it.id.equals(knifeId)) return c;
        return null;
    }

    public static Rarity rarityOf(String knifeId) {
        for (Crate c : CRATES) for (Item it : c.items) if (it.id.equals(knifeId)) return it.rarity;
        return Rarity.MORTAL;
    }

    // ------------------------------------------------------------------ promo codes (only hashes ship)
    private static final String SALT = "zeus-crates/v1:";
    private static final String[][] CODES = {
            {"b24fdcaaeae2ba1c4b93870c6d9118dbbd875c0b55351b9183cfb4be73399f5e", "olympus:1"},
            {"6718e66764b532e44099122170a4264d27aca09f640028078bb433c61283586e", "tempest:1"},
            {"10f1232395c1faa8b2c7f33de56c22c653bb9cc61655ea0c6a8262a13a89c6d9", "hades:1"},
            {"4a275448bd08bafda5e3d938c5c8dab872c3ba2596c611bff6970bf6cc34c192", "nyx:1"},
            {"26aa4d5d61d2645cb585aa4804a9b67b55ed42fffc859b474f667ee48332de11", "poseidon:1"},
            {"4a16fad809b3ff796ec1e9368ea66a08bb0a1c5162a5a37603633093c927fd52", "titan:1"},
            {"ef1fc15f1f67d9bee21df5421c55483a48a7a39e86e06d64d129448746cd7552", "olympus:1"},
            {"b744fccf81280c30449110f0eea18e89dab36755640b12304e32d53b38553fcb", "tempest:1"},
            {"71cb0cc7ba03bdf0cb9443ef1900a71e864848afec968119f4baf4fc062ed823", "hades:1"},
            {"60c172655ac557267c6d4b0ad9eba73b80467b27ffbd6fd25494405f3fd58f1d", "nyx:1"},
            {"dc71e9afd97ed4f267bef12957b186aebc8497aae383b8622229b19f9d34b4fb", "poseidon:1"},
            {"eaf881392eae913d4c250426f2dfd4e7c4a60380b2620bd10ae5aa2fb659e5f4", "titan:1"},
            {"e83433e8a1e889d97f1cb38a501a92872ee7a6a37e1eb4a19c807d077d3ad497", "random:1"},
            {"6ae9bcae958a658df4ac4df32201f48735efd2cb4179264f5fdd9978018dffdf", "random:1"},
            {"8d3390d145905b700a65b56cd4f483ffb258509ce411a983f488e0129399bdcf", "random:1"},
            {"0620e5852b10f3290b5b7af616ea0abda4fc5d5bf3fab859272221e6cd0d9b17", "random:1"},
            {"dd39df5f91612c820228bb0c80eac09af53a9fe608d16b607e36b814548124ef", "random:1"},
            {"d996da80f9d799993ae8c10959bf5fc6cc28952be8929a01c271bbf0c56d034b", "random:1"},
            {"985f50665a8f4b0379c776e6e9f5f5bf036d58a04e7300df760712053e9114e9", "random:2"},
            {"1047049646f83291dc4d283a8c552e4003988b8675342c24a80f13847fb503de", "random:2"},
            {"c54a27f1a8963fb13f2e02c330615a6be86598f4fc54b4683a09a5e5cf17ad4d", "random:2"},
            {"c1f6e6ecde1405ffc3173ab2a3851e88761b071132743714af3bdcd38189a72f", "random:2"},
            {"d4504159ae8a279e806d6bb5c09ab6450f56576b08f8ccc8fdf4321cabc4add7", "olympus+hades+nyx:1"},
            {"7ea743b2a423332ca763c9aa3140974e3f3fce86acf0e95013534e90470f8eb8", "tempest+poseidon+titan:1"},
            {"05fde1635bc1871f37a4108d5889b2302e47724f36c74f148f2166240af51d19", "all:1"},
            {"94d23f92488f01c95b9c7990c59a1f1cd417d9c6052c614cfd327573da8d668c", "nyx:1"},
            {"91babd45f783fb38ad89bf9159b95897c2122c73faf84ac9a6088e97f6b26a76", "random:2"},
            {"32f03d6f62aac63fd11309b7fbb9b79b52aa798357d11498a0f13a8f2011144d", "all:1"},
            {"d7a387e394c529edb401fe7ed8b974999d28c8bd50b373bec50ff7630322ef85", "olympus+hades+nyx:1"},
            {"2cab30e4622229e1c4420a73a6a095435d15f0c62b93de21daaed2d197e816c5", "random:2"},
            {"ecbc07d69d63c9263eb23dd07172dbbde417cba41a814b00b594e0445360d209", "titan:1"},
            {"82a3177ffd8d578bc5e5aaaffba37fa2cb816498067f75843657cb627df0787f", "tempest:1"},
            {"b08de2b96e8a5934a8b829e97b36e7995797d893e2e852c43afd7179371dce20", "titan:1"},
            {"b9f7e09fc2bb460af8367bba7da52ba56802738e6a5276a73d86216e4c272f55", "olympus:1"},
            {"51be361315df6937272160d3ed8f473fe2fd06290a4e5db2adcbb5ecccd5876f", "poseidon:1"},
            {"0c3ec03345ed5e3927c3c0ad8255c05a30bae1c1d74808b550d59737161cd7d4", "random:2"},
            {"bab6d36ab2224708f62943029baebb412926df8ae129f886d381aae2c8203046", "random:1"},
            {"e9b554c13172483b2b3b0f5abe6a02465369445ed3acb55d85ab1a4ca8d8a911", "random:1"},
            {"812b4394a2617d55d345c1246f02838bb167e0a50e96b4650adff24b32faad35", "all:1"},
            {"6e707de8627b9c0f219733915225a2dd3891fa0ab0cbd9210db29406a65e771d", "titan:1"},
            {"5178db88191b1960eeefac79506231fa8310045d356050e927292c3d5d65e45a", "random:1"},
            {"c8fc6a15639b6a1692863e908761c260870ba9fb57f6f98bb21195ded2c76792", "tempest:1"},
            {"6e79d51b4ed0d67bfc4e937c1b514378bff8db2b049f82e116c511a508990a0f", "hades:1"},
            {"6c6988cd02a53579063ff8dff8b0355a5905d998bbef3651c89dc6778c2a3b48", "random:2"},
            {"aa85b10a979b95284d28d0f3c00d6d5f5aa9f2383343cf455adcdb8870dfb32e", "tempest+poseidon+titan:1"},
            {"2512557a03f8d0049716dc7c03ac3ea758b9ef982054dabcb00a1a1ee8345666", "tempest+poseidon+titan:1"},
            {"37ac89463090f6b3a306052040e693dcb026f6b6f8c0fb2dfee4c939610c3c46", "titan:1"},
            {"41a027f60c56967523d6a82fe317a3426750176dfd71b9dcf307e6ce06644118", "all:1"},
            {"f589f7b474ead03d158fd461776c8cd4bb1f01a2d70c7fa87f82353fbdcbfad0", "hades:1"},
            {"7b0647fa45ff2245e8288dd7971a803b53d41fbb3b88d409dd66db658b4e2214", "olympus+hades+nyx:1"},
            {"3d2f01653d0d6e1a3701dc785f11fe4c6d137d76a2067c65f9f1f44bc0a36fe1", "random:1"},
            {"fbd7dfee0b6e177f3e1e0d003561c03409598be46c7461030ff9ddbb734ce960", "random:1"},
            {"062ef82340f669641e0e9679553138aa826af16c98b78a9eb5910138425f9ebe", "random:1"},
            {"53a2b1ce1b8396e1cb250909757961c8f96a14675cc35e038bda7833031265d6", "random:1"},
            {"0235b64274381e2db17183222d8b2f078a4773dc620bebdda8a44929317e49eb", "random:1"},
            {"57620791751a81479e1aa2bf82e6928a6306ab41b0c2954f48eaecb21714c3b7", "random:1"},
            {"125a7f726c70b79de32b12ec21925704aeab070ebf33cfd2bc3f898b911867af", "poseidon:1"},
            {"f4018eb091a6d17c144b59c5731a9733b0df73b0bd9c1699cd8f9d6c08b1eed3", "tempest:1"},
            {"268ee580cc28aa6bf2fec580244eabfbf4e9a9774b0af6c012d07815e932f0be", "random:1"},
            {"d4bf5b0406a2430c679a29956f278d2cd071624b37445fde4da7c23f72152c0b", "tempest+poseidon+titan:1"},
            {"b49ae50f36166fe0ee2aef47c70694947c621ff1f660d5a07e74ef4d1c4ec362", "random:2"},
            {"4388ae535de074480c200265dfdfdcba25c3cbd6db0ab8d390c1078592b1abce", "random:1"},
            {"ac925f0dc3c8ae9bbff52cc157673f7ced0f2714d1beb6048efceff3e894c24b", "random:1"},
            {"61c98af1982a4e4c1dc0ccb2fc3a90b6ef25cf0ca85c74595ac0975f6c3700a0", "poseidon:1"},
            {"9d7b9ceb65db8fb73f1706fc6376dd32e1e66ba02d11c913f55752e1c7459901", "hades:1"},
            {"5ffebd5965fad2924857a4b063877f74ee5cfa92e0d90f6182e9622d60ef6e3b", "titan:1"},
            {"c4c9935ff2396bf6193f39e909e4714b0ef6b522bd83a89702bbb8591896f405", "random:1"},
            {"a803bf43c5dc7938f00fa293d2510b2b19c98e6f1195dcaa92df9444d664b0f2", "random:1"},
            {"889e94d295b70d3fdb269efebfc414b98d97011ca849c2ec1da6148cd5e02519", "tempest+poseidon+titan:1"},
            {"76f0213bb33e6a61b6b40fe65c29216479752951b1886346a02458cbfe198e72", "random:1"},
            {"c0b3d3eed12318dc56be052bdab6f8396debdb914a9991395b0a75d1db8dffdb", "nyx:1"},
            {"f4851ec7fab75416cc54f4130b5ec9319f7cd7ac01b4a21fc31ef1a3817c215d", "olympus:1"},
            {"5743782dd7c8818463eb6fddd9496272c01d668fddf220536b972a22afb46f30", "olympus:1"},
            {"61be0d298e9d65d1867b067c566a38d934df0ab3183a94be1cb9ccf0ebf70fbc", "random:2"},
            {"9bbc9a418b04bd2640cf3ea04a67b9f744560ab6f95dbadee17ba72562c7ee63", "random:2"},
            {"e2070b7e2d9aad06e776ef91b5aa2380618d30989ccdb2b36467bfc8ec9eae8c", "olympus+hades+nyx:1"},
            {"0a1c4ade22ffaa28554c719fe895a142b9274f03fca036210a83af8407880bb8", "random:2"},
            {"f8731889364f4cbefe12ec8b6afa06fd9e633d6285b61be1649ad0aa8a6072a8", "poseidon:1"},
            {"447183b7ef6eb20ad93773c2878852a2455c2d37bfc058f8a50a6b757a9f251d", "tempest:1"},
            {"b8433b177ccd7884e9dc556bd5934154f9a7e9f92fd4deee02101ae34bb74b95", "random:1"},
            {"fc18260f2ca0adb6924ba15d950676d9439d15aefedf93a21120ffd2dbc450a0", "poseidon:1"},
            {"02ff6bb3ec1de9d8cba399f9cacdb251b171e0cd6539dd82ca38444aaac66e40", "random:2"},
            {"e56793ba84251495369ea492c751b72f22695bd92aababa5d02b2d9eb0e8a916", "random:2"},
            {"5866485c4a22e56714d8a533e068c0cb53264c708dff79a2ae9cce2259fe5475", "olympus+hades+nyx:1"},
            {"15cbf948ae9ead37dc247c1b84564eafdc3678e3a1e959bbc022f67e5deca034", "random:2"},
            {"ae2a7480aec334083bc846174c972239f00298eca0516deb2eca7f31392ce42d", "random:1"},
            {"4f99554d2430deee7f2d86024772853953769bfd5ca88b3ad2bab690ed4cf8d8", "random:2"},
            {"369a4c35463bae936a07820e4332c9d870e078548a65e3aeedabddce3e803fe2", "random:1"},
            {"26375a21c6b4cc8ed80c99abdb1e32d71f7069695cc723102df28eb46eb8154e", "hades:1"},
            {"9797f57c5914d7c68528cbf249715624e6d084e4cf58cc339609837af430c1e9", "titan:1"},
            {"50626a80b4cd6c7d333b232a2f9d26a5fe8e2a02d545ff6a66fe22b6025531e8", "random:2"},
            {"3bbeb6aed01054bb33bfc9c1f2cbf55f83d19931486d6a09c55b6c9f560bc7d7", "nyx:1"},
            {"071e537a87fcdc62b20f5163e819d7a3be4d4a25fc3fa27c30393234d906c7a5", "poseidon:1"},
            {"6f96d66086381e69a10013fc8e952f2f90d00d249275e587e8364661a23fcd45", "random:2"},
            {"ddecf4c28e74fce064f920a868481693b63cf32ce47b1836e2b278a0a6cbc628", "all:1"},
            {"1bd47647dc146d73939a77d6a6bdf8e8a6aa521c4466901173c7013dacb67885", "random:1"},
            {"ebe364a5f37fdfb45324e9f506149ded6c89fe22bbd04967577fbe26aa862c3b", "random:1"},
            {"9d19e38382a0bcd4e5a73cd1df9b312af5092b5a5c476610a3df72af3cad42db", "olympus:1"},
            {"0ad9e3d74248a3d86f2a587deedda0578eca3d824b0a35e49215668760fe74bf", "olympus:1"},
            {"f316b710a79fb259b630b07f3962e8886bfde3c0157efa833b45982883682288", "random:2"},
            {"39f1f8338b2771cf23a86281d0459e935f02a2bbd79579339b191cd0d019c929", "tempest:1"},
            {"c06cf16abc44bc2572a9843f1fac63f7576429640a2da6fdce6daf25f64b44f8", "random:1"},
            {"70c28ae017d42d133f50aa3af4c7e719c8217afbc90bad279cf084cedba4c5da", "nyx:1"},
            {"87e643bef390b2e22699c64b46704b47afda9f74332e825ae1175a126eaeff90", "random:1"},
            {"e7d8b97be41e5a0b530f72524dd1fdd076af4e39ec30a0c8c4476d53cb87c8f7", "hades:1"},
            {"92b5fff485a7cbfebaa21ac65778755b4903857eed3fc6dadc2d369b73a1176c", "olympus:1"},
            {"57025d0b4866acc2f3ad118e691d40c5edd7453fa0ef901c54c83da3bcb6a9f0", "random:1"},
            {"ea7527bd1596c2c0781cd165145a7dd154b12df53c75c7012e0b786c2e634c19", "random:2"},
            {"1ebc6144f8cc31d71e8db2768b9579a03b3dcd2c5f00201a06f7d835fdc735cf", "random:2"},
            {"c13a1a1181c4d1513803f5924d0dcd842d1739b51533fa0593a9db6f37981a7b", "random:2"},
            {"9ed144db3582f1c520cd2bc575012689888a2a2da9e000e7c6053df357c6cb0d", "random:1"},
            {"aad3b304b939b331cda4774d8a3db6dac1a2722ff2be1468e10fbc971b596709", "random:1"},
            {"b6c160fc1465de2a550451ba4bfa73e980a18934751abc58d9d4e130d6ee5582", "all:1"},
            {"f333eb9f83b22c4b5324c933079b2b0bcb2460fe2b3b8172a9c2961f838dc60f", "random:1"},
            {"7fd791c978794e595f482ffc1dd7b037747b778ec350bc080867183f6ad73a22", "random:2"},
            {"cc746be88c3c865cdcfcc850658f9298dd5168cbb9e49289f1ae0abef20fad08", "random:1"},
            {"83da381e9066f2a2b9e6bfb41a1a4018f63b9eefad3ac56a2853b6eafe7147a8", "random:1"},
            {"b40888e56be8ded27040815d195ff1b1b249e06f497fdd931ed60c9e3c2594f3", "tempest:1"},
            {"b94e20d5c7c9967b39c897c578d72a0694f3e9e7fb8311e6aa80b4e2fa0391d5", "hades:1"},
            {"9c603e452f33d4fe90f0f019c0db8c3c01c23541d66650eeea77a444054a83b2", "nyx:1"},
            {"96d2399ddfd16d60d3bd8b022e7528c052ddf7b070f0772f8f09d4681477ae55", "nyx:1"},
            {"1817626fe56470c32b02fc51caab3996b6a8915c1b6cc5349d5a55af178a731b", "random:1"},
            {"6dea54b17c2ad5be3ea52bad89f0d7accb1311c257393758c443f5d347fa684e", "all:1"},
            {"067d5ff5449328b3aa1828c58d970b9ada777f89bcb6272e4a51f4b510836203", "random:1"},
            {"0be4d3a13396c9656696819dcd4ed24e0ef43113aae508b6ca0debe6c733e495", "random:2"}
    };

    // ------------------------------------------------------------------ state
    private static int tokens;
    private static int keys;
    private static final Map<String, Integer> held = new LinkedHashMap<>();
    private static final Set<String> owned = new LinkedHashSet<>();
    private static final Set<String> redeemed = new LinkedHashSet<>();
    private static long activeMs;
    private static long dropMs;
    private static long earned;
    private static int opened;
    private static boolean loaded;
    private static boolean dirty;
    private static long lastSave;
    private static final SecureRandom RNG = new SecureRandom();

    // activity tracking
    private static double lx, ly, lz;
    private static float lyaw, lpitch;
    private static long lastActive;
    private static long lastTick;

    public static int tokens() {
        load();
        return tokens;
    }

    public static int keys() {
        load();
        return keys;
    }

    public static int held(String crateId) {
        load();
        return held.getOrDefault(crateId, 0);
    }

    public static int heldTotal() {
        load();
        int n = 0;
        for (int v : held.values()) n += v;
        return n;
    }

    public static int opened() {
        load();
        return opened;
    }

    public static boolean owns(String knifeId) {
        load();
        return STARTER.equals(knifeId) || owned.contains(knifeId);
    }

    public static int ownedCount() {
        load();
        int n = 1;
        for (String s : owned) if (!STARTER.equals(s)) n++;
        return n;
    }

    /** The knife id of an HD knife index (the order of knives/hd/tactical.json). */
    public static String idOf(int hd) {
        KnifeHD.Model m = KnifeHD.model(hd);
        return m == null ? "" : m.id;
    }

    private static Map<String, Integer> HD;

    public static int hdOf(String id) {
        if (HD == null) {
            Map<String, Integer> m = new HashMap<>();
            for (int i = 0; ; i++) {
                KnifeHD.Model km = KnifeHD.model(i);
                if (km == null) break;
                m.put(km.id, i);
            }
            if (m.isEmpty()) return -1;
            HD = m;
        }
        Integer i = HD.get(id);
        return i == null ? -1 : i;
    }

    public static boolean ownsHd(int hd) {
        return owns(idOf(hd));
    }

    public static int starterHd() {
        int i = hdOf(STARTER);
        return Math.max(0, i);
    }

    /** Milliseconds of active play left before the next token. */
    public static long nextTokenIn() {
        load();
        return Math.max(0, TOKEN_MS - activeMs);
    }

    public static float tokenProgress() {
        load();
        return Math.min(1f, activeMs / (float) TOKEN_MS);
    }

    public static long nextDropIn() {
        load();
        return Math.max(0, DROP_MS - dropMs);
    }

    public static boolean activeNow() {
        return System.currentTimeMillis() - lastActive < IDLE_MS;
    }

    // ------------------------------------------------------------------ actions
    public static boolean buyKey() {
        load();
        if (tokens < KEY_PRICE) return false;
        tokens -= KEY_PRICE;
        keys++;
        save(true);
        return true;
    }

    public record Result(Item item, Crate crate, boolean duplicate, int refund) {
    }

    /** Rolls a knife from a crate without spending anything: the reel is built from these. */
    public static Item roll(Crate c) {
        int total = 0;
        Map<Rarity, List<Item>> by = new HashMap<>();
        for (Item it : c.items) by.computeIfAbsent(it.rarity, k -> new ArrayList<>()).add(it);
        for (Rarity r : by.keySet()) total += r.weight;
        int pick = RNG.nextInt(total);
        for (Rarity r : Rarity.values()) {
            List<Item> l = by.get(r);
            if (l == null) continue;
            if (pick < r.weight) return l.get(RNG.nextInt(l.size()));
            pick -= r.weight;
        }
        return c.items.get(0);
    }

    /** Opens one crate with one key. Returns null when either is missing. */
    public static Result open(Crate c) {
        load();
        if (held(c.id) <= 0 || keys <= 0) return null;
        held.merge(c.id, -1, Integer::sum);
        if (held.get(c.id) <= 0) held.remove(c.id);
        keys--;
        opened++;
        Item it = roll(c);
        boolean dup = owns(it.id);
        int refund = dup ? it.rarity.refund : 0;
        if (dup) tokens += refund;
        else owned.add(it.id);
        save(true);
        return new Result(it, c, dup, refund);
    }

    public record Redeem(boolean ok, String message) {
    }

    public static Redeem redeem(String raw) {
        load();
        String code = raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT).replace(' ', '-');
        if (code.isEmpty()) return new Redeem(false, "Type a code first.");
        if (!code.startsWith("ZEUS-") && code.length() == 8) code = "ZEUS-" + code.substring(0, 4) + "-" + code.substring(4);
        String h = sha(SALT + code);
        for (String[] c : CODES) {
            if (!c[0].equals(h)) continue;
            if (redeemed.contains(h)) return new Redeem(false, "That code has already been used on this account.");
            redeemed.add(h);
            String[] parts = c[1].split(":");
            int n = Integer.parseInt(parts[1]);
            List<String> got = new ArrayList<>();
            List<String> which = new ArrayList<>();
            if (parts[0].equals("all")) for (Crate cr : CRATES) which.add(cr.id);
            else if (parts[0].equals("random")) for (int i = 0; i < n; i++) which.add(CRATES.get(RNG.nextInt(CRATES.size())).id);
            else for (String s : parts[0].split("\\+")) for (int i = 0; i < n; i++) which.add(s);
            for (String id : which) {
                held.merge(id, 1, Integer::sum);
                keys++;
                got.add(crate(id).name);
            }
            save(true);
            return new Redeem(true, "Unlocked " + summary(got) + (which.size() == 1 ? " and a key" : " and " + which.size() + " keys") + ".");
        }
        return new Redeem(false, "That code is not valid.");
    }

    private static String summary(List<String> names) {
        Map<String, Integer> c = new LinkedHashMap<>();
        for (String s : names) c.merge(s, 1, Integer::sum);
        StringBuilder b = new StringBuilder();
        for (Map.Entry<String, Integer> e : c.entrySet()) {
            if (b.length() > 0) b.append(", ");
            b.append(e.getValue() > 1 ? e.getValue() + "x " : "").append(e.getKey());
        }
        return b.toString();
    }

    // ------------------------------------------------------------------ play time
    /** Called every client tick: counts active play towards tokens and crate drops. */
    public static void tick(Minecraft mc) {
        long now = System.currentTimeMillis();
        long dt = lastTick == 0 ? 0 : Math.min(1000, now - lastTick);
        lastTick = now;
        if (mc.player == null || mc.level == null || mc.isPaused()) return;
        load();
        var p = mc.player;
        if (Math.abs(p.getX() - lx) > 0.05 || Math.abs(p.getY() - ly) > 0.05 || Math.abs(p.getZ() - lz) > 0.05
                || Math.abs(p.getYRot() - lyaw) > 0.5f || Math.abs(p.getXRot() - lpitch) > 0.5f || p.swinging) {
            lastActive = now;
        }
        lx = p.getX();
        ly = p.getY();
        lz = p.getZ();
        lyaw = p.getYRot();
        lpitch = p.getXRot();
        if (!activeNow()) return;          // standing AFK earns nothing
        activeMs += dt;
        dropMs += dt;
        if (activeMs >= TOKEN_MS) {
            activeMs -= TOKEN_MS;
            tokens++;
            earned++;
            dirty = true;
            if (tokens == KEY_PRICE) Zeus.toast("Zeus Crates: you have " + KEY_PRICE + " tokens, enough for a key", true);
            else Zeus.toast("Zeus Crates: +1 Zeus token (" + tokens + ")", true);
        }
        if (dropMs >= DROP_MS) {
            dropMs -= DROP_MS;
            Crate c = CRATES.get(RNG.nextInt(CRATES.size()));
            held.merge(c.id, 1, Integer::sum);
            dirty = true;
            Zeus.toast("Zeus Crates: a " + c.name + " dropped for you!", true);
        }
        if (now - lastSave > 30_000L) save(false);
    }

    // ------------------------------------------------------------------ storage
    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("zeus").resolve("crates.dat");
    }

    private static byte[] secret() {
        // spread out so the key is not one plain string in the jar
        byte[] a = "olympus-forge".getBytes(StandardCharsets.UTF_8);
        byte[] b = new byte[32];
        for (int i = 0; i < b.length; i++) b[i] = (byte) (a[i % a.length] * 31 + i * 17 ^ 0x5A ^ (i * i));
        return b;
    }

    private static String sign(String body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret(), "HmacSHA256"));
            return hex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            return "";
        }
    }

    private static String sha(String s) {
        try {
            return hex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            return "";
        }
    }

    private static String hex(byte[] d) {
        StringBuilder b = new StringBuilder();
        for (byte x : d) b.append(String.format("%02x", x));
        return b.toString();
    }

    private static void starter() {
        tokens = 0;
        keys = 0;
        held.clear();
        owned.clear();
        owned.add(STARTER);
        redeemed.clear();
        activeMs = 0;
        dropMs = 0;
        earned = 0;
        opened = 0;
    }

    public static synchronized void load() {
        if (loaded) return;
        loaded = true;
        starter();
        Path f = file();
        if (!Files.exists(f)) {
            save(true);
            return;
        }
        try {
            JsonObject root = JsonParser.parseString(Files.readString(f)).getAsJsonObject();
            String body = root.get("data").getAsString();
            if (!sign(body).equals(root.get("sig").getAsString())) {
                Zeus.LOG.warn("Zeus Crates: crates.dat was edited by hand and has been reset");
                Zeus.toast("Zeus Crates: your crate data was edited outside the game, so it was reset", false);
                save(true);
                return;
            }
            JsonObject d = JsonParser.parseString(body).getAsJsonObject();
            tokens = d.get("tokens").getAsInt();
            keys = d.get("keys").getAsInt();
            activeMs = d.get("active").getAsLong();
            dropMs = d.get("drop").getAsLong();
            earned = d.has("earned") ? d.get("earned").getAsLong() : 0;
            opened = d.has("opened") ? d.get("opened").getAsInt() : 0;
            for (var e : d.getAsJsonObject("crates").entrySet()) held.put(e.getKey(), e.getValue().getAsInt());
            for (var e : d.getAsJsonArray("owned")) owned.add(e.getAsString());
            for (var e : d.getAsJsonArray("redeemed")) redeemed.add(e.getAsString());
            owned.add(STARTER);
        } catch (Exception e) {
            Zeus.LOG.warn("Zeus Crates: could not read crates.dat", e);
            starter();
        }
    }

    public static synchronized void save(boolean now) {
        if (!now && !dirty && System.currentTimeMillis() - lastSave < 120_000L) return;
        lastSave = System.currentTimeMillis();
        dirty = false;
        JsonObject d = new JsonObject();
        d.addProperty("tokens", tokens);
        d.addProperty("keys", keys);
        d.addProperty("active", activeMs);
        d.addProperty("drop", dropMs);
        d.addProperty("earned", earned);
        d.addProperty("opened", opened);
        JsonObject c = new JsonObject();
        held.forEach(c::addProperty);
        d.add("crates", c);
        JsonArray o = new JsonArray();
        owned.forEach(o::add);
        d.add("owned", o);
        JsonArray r = new JsonArray();
        redeemed.forEach(r::add);
        d.add("redeemed", r);
        String body = new Gson().toJson(d);
        JsonObject root = new JsonObject();
        root.addProperty("data", body);
        root.addProperty("sig", sign(body));
        try {
            Files.createDirectories(file().getParent());
            Path tmp = file().resolveSibling("crates.dat.tmp");
            Files.writeString(tmp, new Gson().toJson(root));
            Files.move(tmp, file(), java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
        } catch (Exception e) {
            Zeus.LOG.warn("Zeus Crates: could not save crates.dat", e);
        }
    }
}
