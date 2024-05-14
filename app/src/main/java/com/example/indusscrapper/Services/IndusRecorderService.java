package com.example.indusscrapper.Services;

import static com.example.indusscrapper.Utils.AccessibilityUtil.*;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.Intent;
import android.graphics.Path;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Toast;

import com.example.indusscrapper.MainActivity;
import com.example.indusscrapper.Repository.CheckUpiStatus;
import com.example.indusscrapper.Repository.QueryUPIStatus;
import com.example.indusscrapper.Repository.SaveBankTransaction;
import com.example.indusscrapper.Repository.UpdateDateForScrapper;
import com.example.indusscrapper.Utils.AES;
import com.example.indusscrapper.Utils.CaptureTicker;
import com.example.indusscrapper.Utils.Config;
import com.example.indusscrapper.Utils.SharedData;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;


public class IndusRecorderService extends AccessibilityService {

    int loginTryCounter = 0;
    boolean shouldLogout = false;
    boolean proceedToPayment = false;
    String lastCurrentBalance = "";
    Handler logoutHandler = new Handler();
    final CaptureTicker ticker = new CaptureTicker(this::processTickerEvent);
    int appNotOpenCounter = 0;
    boolean debitOnce = true, creditOnce = false;
    int failedToReadTransactionCount = 0;
    CheckUpiStatus upiStatusChecker = new CheckUpiStatus();
    private final Runnable logoutRunnable = () -> {
        Log.d("Logout Handler", "Finished");
        shouldLogout = true;
    };
    boolean isReadingDebits = true;

    @Override
    protected void onServiceConnected() {
        ticker.startChecking();
        super.onServiceConnected();
    }

    private void processTickerEvent() {
        Log.d("Ticker", "Processing Event");
        Log.d("Flags", printAllFlags());
        ticker.setNotIdle();

//        if (!SharedData.startedChecking) return;
        if (!MainActivity.isAccessibilityServiceEnabled(this, this.getClass())) {
            return;
        }


        AccessibilityNodeInfo rootNode = getTopMostParentNode(getRootInActiveWindow());
        if (rootNode != null) {
            if (findNodeByPackageName(rootNode, Config.packageName) == null) {
                if (appNotOpenCounter > 4) {
                    Log.d("App Status", "Not Found");
                    relaunchApp();
                    try {
                        Thread.sleep(4000);
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                    appNotOpenCounter = 0;
                    return;
                }
                logoutHandler.removeCallbacks(logoutRunnable);
                logoutHandler.postDelayed(logoutRunnable, 1000 * 60 * 10);
                appNotOpenCounter++;
            } else {
                checkForSessionExpiry();
                listAllTextsInActiveWindow(getTopMostParentNode(getRootInActiveWindow()));
                CheckUpiStatus.loginCallBack callback = new CheckUpiStatus.loginCallBack() {
                    @Override
                    public void onResult(boolean isSuccess) {
                        if (isSuccess) {
                            System.out.println("-----ACTIVE------");
                            Log.d("App Status", "Found");
//                            rootNode.refresh();


                            if (shouldLogout && listAllTextsInActiveWindow(getTopMostParentNode(getRootInActiveWindow())).contains("Statement")) {
                                logout();
                                return;
                            }


                            loginButton();
                            enterPin();

                            if ((listAllTextsInActiveWindow(getTopMostParentNode(getRootInActiveWindow())).contains("Account Details"))) {
                                getCurrentBalance();
                            }
//                            rootNode.refresh();

                            if (listAllTextsInActiveWindow(getTopMostParentNode(getRootInActiveWindow())).contains("Statement")) {
                                statementButton();
                            }
//                            rootNode.refresh();

                            if (failedToReadTransactionCount > 6) {
                                performGlobalAction(GLOBAL_ACTION_BACK);
                                failedToReadTransactionCount = 0;
                                ticker.setNotIdle();
                                return;
                            }

                            if (debitOnce && listAllTextsInActiveWindow(getTopMostParentNode(getRootInActiveWindow())).contains("Transaction History")) {
                                AccessibilityNodeInfo debitBtn = findNodeByText(getTopMostParentNode(getRootInActiveWindow()), "Debit", false, true);
                                ticker.setNotIdle();
                                if (debitBtn != null) {

                                    debitBtn.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                                    debitBtn.recycle();
                                    debitOnce = false;
                                    isReadingDebits = true;
                                    failedToReadTransactionCount = 0;
                                } else {
                                    failedToReadTransactionCount++;
                                }
                                return;
                            }

                            if (creditOnce && listAllTextsInActiveWindow(getTopMostParentNode(getRootInActiveWindow())).contains("Transaction History")) {
                                AccessibilityNodeInfo creditBtn = findNodeByText(getTopMostParentNode(getRootInActiveWindow()), "Credit", false, true);
                                ticker.setNotIdle();
                                if (creditBtn != null) {
                                    creditBtn.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                                    creditBtn.recycle();
                                    creditOnce = false;
                                    isReadingDebits = false;
                                    failedToReadTransactionCount = 0;
                                } else {
                                    failedToReadTransactionCount++;
                                }
                                return;
                            }

                            if (listAllTextsInActiveWindow(getTopMostParentNode(getRootInActiveWindow())).contains("Transaction History")) {
                                readTransactions();
                            }
                            System.out.println("-----END ACTIVE------");

                        } else {
                            System.out.println("-----IN ACTIVE------");
                            closeAndOpenApp();
                            System.out.println("-----END ACTIVE------");
                        }

                    }
                };
                upiStatusChecker.checkUpiStatus(callback);


            }
            rootNode.recycle();
        }
    }

    private void loginButton() {
        if (loginTryCounter > 8) {
            performGlobalAction(GLOBAL_ACTION_HOME);
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            performGlobalAction(GLOBAL_ACTION_RECENTS);
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            AccessibilityNodeInfo root = getRootInActiveWindow();
            if (root != null) {
                Rect outBounds = new Rect();
                root.getBoundsInScreen(outBounds);
                swipe(outBounds.centerX(), outBounds.centerY(), outBounds.centerX(), 0, 1500);
                loginTryCounter = 0;
                return;
            }
        }
        AccessibilityNodeInfo targetNode = findNodeByText(getTopMostParentNode(getRootInActiveWindow()), "Login", false, true);
        if (targetNode != null) {
            loginTryCounter++;
            Log.d("Update: ", "Found Login Node");
            targetNode.performAction(AccessibilityNodeInfo.ACTION_CLICK);
            targetNode.recycle();
            ticker.setNotIdle();
        }
    }

    private void getCurrentBalance() {
        List<String> out = listAllTextsInActiveWindow(getTopMostParentNode(getRootInActiveWindow()));

        Log.d("Balance nodes", out.toString());
        for (String str : out) {
            if (str.contains("₹")) {
                Log.d("Balance node", str);
                lastCurrentBalance = str.replaceAll(" ", "").replaceAll("₹", "");
            }
        }
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        ticker.setNotIdle();
    }

    private void relaunchApp() {
        // Might fail not tested
        if (MainActivity.isAccessibilityServiceEnabled(this, this.getClass())) {
            new QueryUPIStatus(() -> {
                Intent intent = new Intent();
                intent.setClassName(Config.packageName, Config.activityName);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
            }, () -> {
                Toast.makeText(this, "Scrapper inactive", Toast.LENGTH_SHORT).show();
            }).evaluate();
        }
    }

//    private void checkForIncomingPayment() {
//        new CheckForIncomingPayment(() -> {
//            // Found Incoming Payment
//            proceedToPayment = true;
//        }).evaluate();
//    }

    private void closeAndOpenApp() {
        // Close the current app
        performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK);

        String packageName = "com.app.indusScrapper";

        Intent intent = getPackageManager().getLaunchIntentForPackage(packageName);
        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        } else {
            Log.e("AccessibilityService", "App not found: " + packageName);
        }


    }

    public void readTransactions() {
        JSONArray output = new JSONArray();
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        List<String> unfilteredTransactionInfo = listAllTextsInActiveWindow(getTopMostParentNode(getRootInActiveWindow()));
        Log.d("Transaction Output", unfilteredTransactionInfo.toString());
        unfilteredTransactionInfo.removeIf(String::isEmpty);

        List<List<String>> transactions = filterOutTransactionData(unfilteredTransactionInfo);
        for (List<String> transaction : transactions) {
            try {
                AtomicReference<String> transactionInfo = new AtomicReference<>();
                transaction.forEach((str) -> {
                    if (str.contains("/")) {
                        transactionInfo.set(str);
                        Log.d("TYPEOFDATA", "Setting DEBIT: " + getUPIId(transactionInfo.get()).isEmpty());
                    }
                });
                if (getUploadData(transaction, getUPIId(transactionInfo.get()).isEmpty()) != null) {
                    JSONObject object = getUploadData(transaction, getUPIId(transactionInfo.get()).isEmpty());
                    output.put(object);
                }

            } catch (Exception ignored) {
                failedToReadTransactionCount++;
            }
        }
        Log.d("API BODY", output.toString());
        JSONObject result = new JSONObject();
        try {
            result.put("Result", AES.encrypt(output.toString()));
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
        if (output.length() > 0) {
            new QueryUPIStatus(() -> {
                ticker.setNotIdle();
                new SaveBankTransaction(() -> {
                    ticker.setNotIdle();
                    Rect outBounds = new Rect();
                    getRootInActiveWindow().getBoundsInScreen(outBounds);
                    int startX = (outBounds.width() / 2);
                    int startY = outBounds.height() / 2;
                    int endY = 200;
                    swipe(startX, startY, startX, endY, 2000);
                    ticker.setNotIdle();
                }, () -> {
                    ticker.setNotIdle();
                    if (!isReadingDebits) {
                        performGlobalAction(GLOBAL_ACTION_BACK);
                        debitOnce = true;
                        creditOnce = false;
                    } else {
                        creditOnce = true;
                    }
                }).evaluate(result.toString());

                new UpdateDateForScrapper().evaluate();
                ticker.setNotIdle();
            }, () -> {
                debitOnce = true;
                creditOnce = false;
            }).evaluate();
        }
        ticker.setNotIdle();

    }

    public void statementButton() {
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        AccessibilityNodeInfo statementButton = findNodeByText(getTopMostParentNode(getRootInActiveWindow()), "Statement", false, false);

        if (statementButton != null) {
            Rect outBounds = new Rect();
            statementButton.getBoundsInScreen(outBounds);
            performTap(outBounds.centerX(), outBounds.centerY(), 500);
            debitOnce = true;
            creditOnce = false;
        }

    }

    public void enterPin() {
        AccessibilityNodeInfo pinPageValidation = findNodeByText(getTopMostParentNode(getRootInActiveWindow()), "Forgot MPIN?", false, false);
        if (pinPageValidation != null) {
            loginTryCounter = 0;
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            Log.d("Trying TODO: ", "LOGIN");
            String[] pinToEnter = Config.loginPin.split("");

            for (String character : pinToEnter) {
                Log.d("PIN", "Finding: " + character);
                AccessibilityNodeInfo pinBox = findNodeByText(getTopMostParentNode(getRootInActiveWindow()), character, false, true);
                if (pinBox != null) {
                    Log.d("PIN", "Found: " + character);
                    pinBox.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                    pinBox.recycle();
                }
            }

            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            Log.d("Logout Handler", "Started");
            logoutHandler.removeCallbacks(logoutRunnable);
            logoutHandler.postDelayed(logoutRunnable, 1000 * 60 * 15);
            ticker.setNotIdle();
        }
    }

    public void logout() {
        Log.d("Logout", "Inside logout");
        ticker.setNotIdle();
        try {
            Log.d("Status", "Time to logout");
            Thread.sleep(2000);
            ticker.setNotIdle();

            Rect outBounds = new Rect();
            getTopMostParentNode(getRootInActiveWindow()).getBoundsInScreen(outBounds);
            performTap(666, 140);
            Thread.sleep(1000);
            ticker.setNotIdle();

            listAllTextsInActiveWindow(getTopMostParentNode(getRootInActiveWindow()));
            Thread.sleep(1000);

            AccessibilityNodeInfo rootNode = getTopMostParentNode(getRootInActiveWindow());
            rootNode.refresh();
            AccessibilityNodeInfo yesBtn = findNodeByText(rootNode, "YES", false, false);
            if (yesBtn != null) {
                yesBtn.performAction(AccessibilityNodeInfo.ACTION_CLICK);
            }
            shouldLogout = false;
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public void checkForSessionExpiry() {
        AccessibilityNodeInfo targetNode1 = findNodeByText(getTopMostParentNode(getRootInActiveWindow()), "Your session is timed out. Please login again.", true, false);
        AccessibilityNodeInfo targetNode2 = findNodeByText(getTopMostParentNode(getRootInActiveWindow()), "Sorry! We could not process your request currently. Please try again.", false, false);
        AccessibilityNodeInfo targetNode3 = findNodeByText(getTopMostParentNode(getRootInActiveWindow()), "No Internet Connection", true, false);
        AccessibilityNodeInfo targetNode4 = findNodeByText(getTopMostParentNode(getRootInActiveWindow()), "Session got invalidated. Please login again!", true, false);
        AccessibilityNodeInfo targetNode5 = findNodeByText(getTopMostParentNode(getRootInActiveWindow()), "Are you sure you want to logout?", true, false);
        AccessibilityNodeInfo targetNode6 = findNodeByText(getTopMostParentNode(getRootInActiveWindow()), "Notification", true, false);
        AccessibilityNodeInfo targetNode7 = findNodeByText(getTopMostParentNode(getRootInActiveWindow()), "Oops! The authentication has failed. Please check your credentials and try again. Remaining attempts", true, false);
        AccessibilityNodeInfo targetNode8 = findNodeByText(getTopMostParentNode(getRootInActiveWindow()), "Sorry! We could not process your request currently. Please try again later.", false, false);
        AccessibilityNodeInfo targetNode9 = findNodeByText(getTopMostParentNode(getRootInActiveWindow()), "We are unable to load your accounts. Please try again.", false, false);
        AccessibilityNodeInfo targetNode10 = findNodeByText(getTopMostParentNode(getRootInActiveWindow()), "We are unable to process your request. Please try again later.", false, false);
        AccessibilityNodeInfo targetNode11 = findNodeByText(getTopMostParentNode(getRootInActiveWindow()), "Do you want to exit?", false, false);

        if (targetNode1 != null || targetNode2 != null || targetNode3 != null || targetNode4 != null || targetNode6 != null || targetNode7 != null || targetNode8 != null || targetNode9 != null || targetNode10 != null) {
            Log.d("Session", "Expired");
            AccessibilityNodeInfo okButtonNode = findNodeByText(getTopMostParentNode(getRootInActiveWindow()), "OK", true, true);
            if (okButtonNode != null) {
                okButtonNode.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                okButtonNode.recycle();
            } else {
                okButtonNode = findNodeByText(getTopMostParentNode(getRootInActiveWindow()), "Ok", true, true);
                if (okButtonNode != null) {
                    okButtonNode.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                    okButtonNode.recycle();
                }
            }

            ticker.setNotIdle();
        }

//        if (targetNode5 != null && !shouldLogout) {
//            AccessibilityNodeInfo noButtonNode = findNodeByText(getTopMostParentNode(getRootInActiveWindow()), "NO", false, true);
//            if (noButtonNode != null) {
//                noButtonNode.performAction(AccessibilityNodeInfo.ACTION_CLICK);
//                noButtonNode.recycle();
//            }
//        }
        if (targetNode5 != null) {
            AccessibilityNodeInfo noButtonNode = findNodeByText(getTopMostParentNode(getRootInActiveWindow()), "YES", false, true);
            if (noButtonNode != null) {
                noButtonNode.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                noButtonNode.recycle();
            }
        }


        if (targetNode11 != null) {
            AccessibilityNodeInfo noButtonNode = findNodeByText(getTopMostParentNode(getRootInActiveWindow()), "Yes", false, false);
            if (noButtonNode != null) {
                noButtonNode.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                noButtonNode.recycle();
            }

        }
    }

    public static List<List<String>> filterOutTransactionData(List<String> arr) {
        Log.d("InList", arr.toString());
        List<String> outList = filterTransactionRelatedInfo(arr);

        Log.d("Outlist", outList.toString());
        outList.removeIf(e -> e.length() <= 1);

        // Combine filtered elements into a single string
        String combinedString = String.join(",,", outList);

        // Define a regex pattern to split at the date element
        Pattern pattern = Pattern.compile("(?=\\d{4}-\\d{2}-\\d{2})");

        // Use the regex pattern to split the string
        String[] splitByDate = pattern.split(combinedString);
        Log.d("Split by data", Arrays.toString(splitByDate));

        // Print the resulting array
        List<List<String>> out = new ArrayList<>();
        for (String element : splitByDate) {
            List<String> transactionInfo = new ArrayList<>(Arrays.asList(element.split(",,")));
            if (transactionInfo.size() > 0) {
                out.add(transactionInfo);
            }
        }
        out.removeIf(List::isEmpty);
        return out;
    }

    private static List<String> filterTransactionRelatedInfo(List<String> array) {
        List<String> filteredList = new ArrayList<>(array);
        for (String str : array) {
            if (!str.contains("-") && !str.contains("₹ ") && !str.contains("/")) {
                filteredList.remove(str);
            }
        }

        return filteredList;
    }

    private void proceedToPayment() {
        String beneficiaryUserId = "01890302411";
        String amount = "50";
//        if (Config.isPersonalApp()) return; // Not implemented yet
        initiatePayment(() ->
                enterCustomerDetails(beneficiaryUserId, () ->
                        enterPaymentAmount(amount, () ->
                                enterConfirmationPin(() -> {
                                            markPaymentComplete();
                                            proceedToPayment = false;
                                        }
                                )
                        )
                )
        );
    }


    private void initiatePayment(Runnable callback) {
        AccessibilityNodeInfo cashInNode = findNodeByText(getTopMostParentNode(getRootInActiveWindow()), "Send Money", true, false);
        if (cashInNode != null) {
            Rect outBounds = new Rect();
            cashInNode.getBoundsInScreen(outBounds);
            performTap(outBounds.centerX(), outBounds.centerY());
            ticker.setNotIdle();
            try {
                Thread.sleep(4000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            ticker.setNotIdle();
            callback.run();
        }
    }

    private void enterCustomerDetails(String beneficiaryUserId, Runnable callback) {
        AccessibilityNodeInfo rootNode = getTopMostParentNode(getRootInActiveWindow());
        rootNode.refresh();

        AccessibilityNodeInfo customerIdEditTextNode = findNodeByText(rootNode, "Enter name or number", true, false);
        if (customerIdEditTextNode != null) {
            Log.d("Update", "Entering customer name");

            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }

            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }

            Bundle customerIdEditTextBundle = new Bundle();
            customerIdEditTextBundle.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, beneficiaryUserId);
            customerIdEditTextNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, customerIdEditTextBundle);

            rootNode.refresh();
            ticker.setNotIdle();

            try {
                Thread.sleep(2500);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }

            rootNode.refresh();
            listAllTextsInActiveWindow(rootNode);
            listAllTextsInActiveWindow(getTopMostParentNode(getRootInActiveWindow()));

            if (findNodeByText(getTopMostParentNode(getRootInActiveWindow()), insertSpaceAtIndex(beneficiaryUserId, 3), true, false) != null) {
                AccessibilityNodeInfo numberNode = findNodeByText(getTopMostParentNode(getRootInActiveWindow()), insertSpaceAtIndex(beneficiaryUserId, 3), true, false);
                Rect outBounds = new Rect();
                numberNode.getBoundsInScreen(outBounds);
                performTap(outBounds.centerX(), outBounds.centerY());
                Log.d("Number node", "Node found");
                ticker.setNotIdle();
                callback.run();
            } else {
                Log.d("Number node", "Node not found");
                AccessibilityNodeInfo proceedBtn = findNodeByText(getTopMostParentNode(getRootInActiveWindow()), "Proceed to the next step", false, false);
                Log.d("Proceed button", String.valueOf(proceedBtn != null));
                if (proceedBtn != null) {
                    proceedBtn.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                    ticker.setNotIdle();
                    callback.run();
                }
            }
        } else {
            listAllTextsInActiveWindow(rootNode);
            Log.d("Update", "input field not found");
        }
    }

    private static String insertSpaceAtIndex(String input, int index) {
        if (index >= 0 && index <= input.length()) {
            // Insert a space at the specified index
            return input.substring(0, index) + " " + input.substring(index);
        } else {
            // Handle invalid index
            return input;
        }
    }

    private void enterPaymentAmount(String amount, Runnable callback) {
        AccessibilityNodeInfo rootNode = getTopMostParentNode(getRootInActiveWindow());
        rootNode.refresh();
        try {
            Thread.sleep(4000);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        ticker.setNotIdle();
        listAllTextsInActiveWindow(rootNode);
        rootNode = getTopMostParentNode(getRootInActiveWindow());
        AccessibilityNodeInfo amountEditTextNode = findNodeByText(getTopMostParentNode(getRootInActiveWindow()), "৳0", false, false);

        if (amountEditTextNode != null) {
            Log.d("Update", "Amount edit text found");

            Bundle amountEditTextBundle = new Bundle();
            amountEditTextBundle.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, amount);
            amountEditTextNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, amountEditTextBundle);

            ticker.setNotIdle();
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }

            rootNode.refresh();
            listAllTextsInActiveWindow(rootNode);
            listAllTextsInActiveWindow(getTopMostParentNode(getRootInActiveWindow()));

            AccessibilityNodeInfo proceedBtn = findNodeByText(getTopMostParentNode(getRootInActiveWindow()), "Proceed to the next step", false, false);
            if (proceedBtn != null)
                proceedBtn.performAction(AccessibilityNodeInfo.ACTION_CLICK);

            ticker.setNotIdle();
            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            ticker.setNotIdle();
            callback.run();
        } else {
            Log.d("Update", "Amount edit text not found");
        }
    }

    private void enterConfirmationPin(Runnable callback) {
        listAllTextsInActiveWindow(getTopMostParentNode(getRootInActiveWindow()));

        AccessibilityNodeInfo pinTextField = findNodeByText(getTopMostParentNode(getRootInActiveWindow()), "Enter PIN", false, false);
        if (pinTextField != null) {
            Log.d("Trying TODO: ", "Enter PIN");
            String[] pinToEnter = Config.loginPin.split("");

            for (String character : pinToEnter) {
                Log.d("PIN", "Finding: " + character);
                AccessibilityNodeInfo pinBox = findNodeByText(getTopMostParentNode(getRootInActiveWindow()), character, false, true);
                if (pinBox != null) {
                    Log.d("PIN", "Found: " + character);
                    pinBox.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                }
            }

            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            Log.d("Trying TODO: ", "Click on confirm button");
            AccessibilityNodeInfo confirmBtn = findNodeByText(getTopMostParentNode(getRootInActiveWindow()), "Confirm", false, true);
            if (confirmBtn != null) {
                Log.d("Update: ", "Click on confirm button");
                confirmBtn.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                ticker.setNotIdle();
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                ticker.setNotIdle();
                callback.run();
            }
            ticker.setNotIdle();
        }
    }

    private void markPaymentComplete() {
        listAllTextsInActiveWindow(getTopMostParentNode(getRootInActiveWindow()));
        AccessibilityNodeInfo holdToConfirmNode = findNodeByText(getTopMostParentNode(getRootInActiveWindow()), "Tap and hold for Cash In", false, false);

        if (holdToConfirmNode != null) {
            Rect confirmBtnBounds = new Rect();
            holdToConfirmNode.getBoundsInScreen(confirmBtnBounds);
            ticker.setNotIdle();

            performTap(confirmBtnBounds.centerX(), confirmBtnBounds.centerY(), 4000);

            ticker.setNotIdle();
            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            ticker.setNotIdle();

            // strip data from screen for transaction details
            AccessibilityNodeInfo rootNode = getTopMostParentNode(getRootInActiveWindow());
            rootNode.refresh();

            List<String> out = listAllTextsInActiveWindow(rootNode);
            listAllTextsInActiveWindow(getTopMostParentNode(getRootInActiveWindow()));

            AccessibilityNodeInfo completeTxnBtn = findNodeByText(getTopMostParentNode(getRootInActiveWindow()), "Back to Home", false, false);
            if (completeTxnBtn != null) {
                completeTxnBtn.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                Log.d("Payment", "Completed");
            }
        }
    }

    private String printAllFlags() {
        StringBuilder result = new StringBuilder();

        // Get the fields of the class
        Field[] fields = getClass().getDeclaredFields();

        for (Field field : fields) {
            field.setAccessible(true);
            String fieldName = field.getName();
            try {
                Object value = field.get(this);
                result.append(fieldName).append(": ").append(value).append("\n");
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            }
        }
        return result.toString();
    }

    public boolean performTap(int x, int y) {
        Log.d("Accessibility", "Tapping " + x + " and " + y);
        Path p = new Path();
        p.moveTo(x, y);
        GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
        gestureBuilder.addStroke(new GestureDescription.StrokeDescription(p, 0, 950));

        GestureDescription gestureDescription = gestureBuilder.build();

        boolean dispatchResult = false;
        dispatchResult = dispatchGesture(gestureDescription, new GestureResultCallback() {
            @Override
            public void onCompleted(GestureDescription gestureDescription) {
                super.onCompleted(gestureDescription);
            }
        }, null);
        Log.d("Dispatch Result", String.valueOf(dispatchResult));
        return dispatchResult;
    }

    public boolean performTap(int x, int y, int duration) {
        Log.d("Accessibility", "Tapping " + x + " and " + y);
        Path p = new Path();
        p.moveTo(x, y);
        GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
        gestureBuilder.addStroke(new GestureDescription.StrokeDescription(p, 0, duration));

        GestureDescription gestureDescription = gestureBuilder.build();

        boolean dispatchResult = false;
        dispatchResult = dispatchGesture(gestureDescription, new GestureResultCallback() {
            @Override
            public void onCompleted(GestureDescription gestureDescription) {
                super.onCompleted(gestureDescription);
            }
        }, null);
        Log.d("Dispatch Result", String.valueOf(dispatchResult));
        return dispatchResult;
    }

    public boolean swipe(float oldX, float oldY, float newX, float newY, long duration) {
        // Set up the Path by swiping from the old position coordinates to the new position coordinates.
        Path swipePath = new Path();
        swipePath.moveTo(oldX, oldY);
        swipePath.lineTo(newX, newY);

        GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
        gestureBuilder.addStroke(new GestureDescription.StrokeDescription(swipePath, 0, duration));

        boolean dispatchResult = dispatchGesture(gestureBuilder.build(), null, null);

        try {
            Thread.sleep(duration / 1000);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        return dispatchResult;
    }

    public JSONObject getUploadData(List<String> paymentInfoArray, boolean isDebit) {

        // Sample (real) input for this function: [2023-10-03, ¥505.00, UPI/327616081698/CR/HRID/PYTM, /rdeshpal2@paytm/Sent]
        // Tesseract being tesseract read rupee sign as ¥

        Log.d("Payment Info Array", paymentInfoArray.toString());
        String amount = "";
        String date = "";
        String transactionInfo = "";

        for (String str : paymentInfoArray) {
            if (str.contains("₹")) {
                amount = str.replaceAll("₹", isDebit ? "-" : "").replaceAll(" ", "");
            }
            if (str.contains("/")) {
                transactionInfo = str;
            }
            if (str.contains("-")) {
                date = str;
            }
        }

        if (transactionInfo.isEmpty() || amount.isEmpty()) {
            return null;
        }

        JSONObject jsonObject = new JSONObject();

        try {
            jsonObject.put("Description", getTheUTRWithoutUTR(transactionInfo));
            jsonObject.put("UPIId", getUPIId(transactionInfo));
            jsonObject.put("CreatedDate", date);
            jsonObject.put("Amount", amount);
            jsonObject.put("RefNumber", getTheUTRWithoutUTR(transactionInfo));
            jsonObject.put("AccountBalance", lastCurrentBalance);
            jsonObject.put("BankName", Config.bankName + "-" + Config.bankLoginId);
            jsonObject.put("BankLoginId", Config.bankLoginId);
            jsonObject.put("DeviceInfo", SharedData.getDeviceInfo(this));

        } catch (JSONException e) {
            throw new RuntimeException(e);
        }

        return jsonObject;
    }

    private String getUPIId(String description) {
        try {
            if (!description.contains("@")) return "";
            String[] split = description.split("/");
            String value = null;
            value = Arrays.stream(split).filter(x -> x.contains("@")).findFirst().orElse(null);
            return value != null ? value : "";
        } catch (Exception ex) {
            Log.d("Exception", ex.getMessage());
            return "";
        }

    }

    private static String getTheUTRWithoutUTR(String description) {
        try {
            String[] split = description.split("/");
            String value = null;
            value = Arrays.stream(split).filter(x -> x.length() == 12).findFirst().orElse(null);
            if (value != null) {
                return value + " " + description;
            }
            return description;
        } catch (Exception e) {
            return description;
        }

    }

    // Unused AccessibilityService Callbacks
    @Override
    public void onInterrupt() {
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
    }

}
