package tw.cchi.tkbbooking;

import org.json.JSONArray;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

public class Main {
    private static final boolean LOAD_FROM_ARGS = true;

    private static BotWebClient botWebClient;

    public static void main(String[] args) {
        /**
         * classNo:
         *  "V5<6:TJ:;<=>?A=BD@EGCI" 線性代數(黃子嘉)
         *  "V5<6>KLM;<=>@B=BD@EJCI" 計組與計結(張凡)
         *  "V5<6>KM:;<=>?A=BD@EHCI" 離散數學(黃子嘉)
         *  "V5<6>KM];<=>?A=BD@EICI" 資料結構(洪逸)
         *  "V5<6>K:;<=>?A=BD@EKCI" 作業系統(洪逸)
         *  "V5<77KL;<=>?B=BD@ELCI" 演算法(林立宇)
         * date:
         *  ex. "2018-07-23"
         * branchNo:
         *  [台南] "WW"
         *  [台北] "TT"
         *  [楠梓] "XB"
         * sessionTimes:
         *  [場次: 1 09:00~12:20]: 1
         */
        // --------------------------------------------- //
        String classNo = "V5<6:TJ:;<=>?A=BD@EGCI";
        String date = "2018-07-28";
        String branchNo = "WW";
        int sessionTime = 3;
        int sleep = 500;
        // --------------------------------------------- //

        // Load from args
        if (LOAD_FROM_ARGS) {
            if (args.length == 5) {
                System.out.println("Loading from args...");
                try {
                    classNo = args[0];
                    date = args[1];
                    branchNo = args[2];
                    sessionTime = Integer.parseInt(args[3]);
                    sleep = Integer.parseInt(args[4]);
                } catch (NumberFormatException e) {
                    printUsages();
                    return;
                }
            } else {
                printUsages();
                return;
            }
        }

        System.out.println("classNo: " + classNo);
        System.out.println("date: " + date);
        System.out.println("branchNo: " + branchNo);
        System.out.println("sessionTime: " + sessionTime);
        System.out.println("sleep: " + sleep);
        System.out.println();

        botWebClient = new BotWebClient();

        System.out.print("Logging in...");
        if (!login()) {
            System.err.println("Error logging in.");
            return;
        }
        System.out.println("success.");

        while (getSeatLeft(date, branchNo, sessionTime) == 0) {
            try {
                Thread.sleep(sleep);
            } catch (InterruptedException ignored) {
            }
        }

        boolean bookSuccess = book(classNo, date, branchNo, Arrays.asList(sessionTime));

        System.out.println("bookSuccess = " + bookSuccess);
    }

    private static boolean login() {
        String html = botWebClient.sendGet("http://bookseat.tkblearning.com.tw/book-seat/student/login/");

        // Check if had logged in
        String strCheck = "javascript:location.href='/book-seat/student/login/logout'";
        if (html.contains(strCheck))
            return true;

        Document doc = Jsoup.parse(html);
        String accessToken = doc.select("form[name=listform] input[name=access_token]").val();

        HashMap<String, String> reqProperties = new HashMap<>();
        reqProperties.put("Host", "bookseat.tkblearning.com.tw");
        reqProperties.put("Origin", "http://bookseat.tkblearning.com.tw");
        reqProperties.put("Referer", "http://bookseat.tkblearning.com.tw/book-seat/student/login/toLogin");

        HashMap<String, String> formParams = new HashMap<>();
        formParams.put("access_token", accessToken);
        formParams.put("id", Config.USERNAME);
        formParams.put("pwd", Config.PASSWORD);

        try {
            botWebClient.sendPost("http://bookseat.tkblearning.com.tw/book-seat/student/login/login", formParams, reqProperties);
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }

        return true;
    }

    private static int getSeatLeft(String date, String branchNo, int sessionTime) {
        HashMap<String, String> reqProperties = new HashMap<>();
        reqProperties.put("Host", "bookseat.tkblearning.com.tw");
        reqProperties.put("Origin", "http://bookseat.tkblearning.com.tw");
        reqProperties.put("Referer", "http://bookseat.tkblearning.com.tw/book-seat/student/bookSeat/index");

        HashMap<String, String> formParams = new HashMap<>();
        formParams.put("date", date);
        formParams.put("branch_no", branchNo);

        String json;
        try {
            json = botWebClient.sendPost("http://bookseat.tkblearning.com.tw/book-seat/student/bookSeat/sessionTime", formParams, reqProperties);
        } catch (Exception e) {
            e.printStackTrace();
            return 0;
        }

        JSONArray jsonArray = new JSONArray(json);
        for (int i = 0; i < jsonArray.length(); i++) {
            JSONObject jsonSegment = jsonArray.getJSONObject(i);

            int segmentNum = Integer.parseInt(jsonSegment.get("SEGMENT").toString());
            if (segmentNum == sessionTime) {
                boolean hasClass = jsonSegment.get("HASCLASS").toString().equals("1");
                boolean allowBook = jsonSegment.has("SEAT_MSG") && jsonSegment.get("SEAT_MSG").toString().equals("Y");
                boolean offClass = jsonSegment.get("OFFCLASS").toString().equals("1");
                int seatLeft = Integer.parseInt(jsonSegment.get("SEATNUM").toString());

                if (hasClass) {
                    throw new Error("You have already booked the target segment.");
                } else {
                    if (offClass) {
                        System.out.printf("Off class, seat left=%d\n", seatLeft);
                        return 0;
                    } else if (!allowBook) {
                        System.out.printf("Booking not allowed, seat left=%d\n", seatLeft);
                        return 0;
                    } else {
                        System.out.printf("Seat left=%d\n", seatLeft);
                        return seatLeft;
                    }
                }
            }
        }

        return 0;
    }

    private static boolean book(String classNo, String date, String branchNo, List<Integer> sessionTimes) {
        String html = botWebClient.sendGet("http://bookseat.tkblearning.com.tw/book-seat/student/bookSeat/index");
        String keyword = "access_token : \"";
        int index = html.indexOf(keyword) + keyword.length();
        String accessToken = html.substring(index, index + 36);

        HashMap<String, String> reqProperties = new HashMap<>();
        reqProperties.put("Host", "bookseat.tkblearning.com.tw");
        reqProperties.put("Origin", "http://bookseat.tkblearning.com.tw");
        reqProperties.put("Referer", "http://bookseat.tkblearning.com.tw/book-seat/student/bookSeat/index");

        HashMap<String, String> formParams = new HashMap<>();
        formParams.put("class_data", classNo);
        formParams.put("date", date);
        formParams.put("branch_no", branchNo);
        formParams.put("access_token", accessToken);
        for (int sessionTime : sessionTimes)
            formParams.put("session_time[]", String.valueOf(sessionTime));

        try {
             botWebClient.sendPost("http://bookseat.tkblearning.com.tw/book-seat/student/bookSeat/book", formParams, reqProperties);
        } catch (Exception e) {
            System.err.println(e.getMessage());
            return false;
        }

        return true;
    }

    private static void printUsages() {
        System.out.println("Usage: <jar> classNo date branchNo sessionTime sleepInterval");
        System.out.println(
            " * classNo:\n" +
            " *  \"V5<6:TJ:;<=>?A=BD@EGCI\" 線性代數(黃子嘉)\n" +
            " *  \"V5<6>KLM;<=>@B=BD@EJCI\" 計組與計結(張凡)\n" +
            " *  \"V5<6>KM:;<=>?A=BD@EHCI\" 離散數學(黃子嘉)\n" +
            " *  \"V5<6>KM];<=>?A=BD@EICI\" 資料結構(洪逸)\n" +
            " *  \"V5<6>K\u007F:;<=>?A=BD@EKCI\" 作業系統(洪逸)\n" +
            " *  \"V5<77KL\u007F;<=>?B=BD@ELCI\" 演算法(林立宇)\n" +
            " * date:\n" +
            " *  ex. \"2018-07-23\"\n" +
            " * branchNo:\n" +
            " *  [台南] \"WW\"\n" +
            " *  [台北] \"TT\"\n" +
            " *  [楠梓] \"XB\"\n" +
            " * sessionTimes:\n" +
            " *  [場次: 1 09:00~12:20]: 1");
    }
}
