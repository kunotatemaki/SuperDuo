package it.jaschke.alexandria;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.support.design.widget.Snackbar;
import android.support.v4.app.Fragment;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.LocalBroadcastManager;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.Patterns;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import it.jaschke.alexandria.data.AlexandriaContract;
import it.jaschke.alexandria.services.BookService;
import it.jaschke.alexandria.services.DownloadImage;
import me.dm7.barcodescanner.zbar.BarcodeFormat;


public class AddBook extends Fragment implements LoaderManager.LoaderCallbacks<Cursor> {
    private static final String TAG = "INTENT_TO_SCAN_ACTIVITY";
    private static final int BARCODE_SCAN_RESULT = 756;
    private EditText ean;
    private final int LOADER_ID = 1;
    private View rootView;
    private final String EAN_CONTENT="eanContent";
    private static final String SCAN_FORMAT = "scanFormat";
    private static final String SCAN_CONTENTS = "scanContents";

    private String mScanFormat = "Format:";
    private String mScanContents = "Contents:";
    private BarcodeFormat barcodeFormat = BarcodeFormat.ISBN13;
    private String eanInTextEditOrScannerActivity = ""; //stores isbn typed or scanned



    public AddBook(){
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if(ean!=null) {
            outState.putString(EAN_CONTENT, ean.getText().toString());
        }
    }

    @Override
    public View onCreateView(final LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

        rootView = inflater.inflate(R.layout.fragment_add_book, container, false);

        eanInTextEditOrScannerActivity = "";

        ean = (EditText) rootView.findViewById(R.id.ean);

        ean.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                //no need
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                //no need
            }

            @Override
            public void afterTextChanged(Editable s) {
                String sEan =s.toString();
                //catch isbn10 numbers
                eanInTextEditOrScannerActivity = s.toString();

                if(sEan.length()<13){
                    //ISBN-10 mode allows letter only in check digit. Check it
                    if(barcodeFormat == BarcodeFormat.ISBN10){
                        for(int i=0; i<sEan.length(); i++){
                            if(!Character.isDigit(sEan.charAt(i))) {
                                if (i != 9) { //only 10th digit can be a letter X
                                    //remove it
                                    StringBuilder sb = new StringBuilder(sEan);
                                    sb.deleteCharAt(i);
                                    sEan = sb.toString();
                                    ean.setText(sEan);
                                    ean.setSelection(ean.getText().length());
                                    Toast.makeText(getActivity(), getResources().getString(R.string.only_digits), Toast.LENGTH_SHORT).show();
                                    return;
                                } else {
                                    String check = String.valueOf(sEan.charAt(i));
                                    if (!check.toLowerCase().equals("x")) {
                                        StringBuilder sb = new StringBuilder(sEan);
                                        sb.deleteCharAt(i);
                                        sEan = sb.toString();
                                        ean.setText(sEan);
                                        ean.setSelection(ean.getText().length());
                                        Toast.makeText(getActivity(), getResources().getString(R.string.only_x), Toast.LENGTH_SHORT).show();
                                        return;
                                    }else{
                                        searchBook(sEan);
                                    }
                                }
                            }

                        }
                    }
                    clearFields();
                    return;
                }

                searchBook(sEan);

            }
        });

        //This RadioGroup is for properly handle isbn-10 types. Previously, when user did typed 10 digits (not starting with 978)
        //the program automatically assumed an isbn10. There were two problems
        //1.- ISBN-13 may start with 979. In that case the program will start an unnecessary ISBN-10 search when 10th
        //digit is typed
        //2.- TextEdit only allowed numbers and ISBN-10 check-digit can be X (instead of 10). So in that case, letter X
        //should be accepted for 10th digit
        RadioGroup isbnTypes = (RadioGroup)rootView.findViewById(R.id.isbn_types);
        isbnTypes.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                InputFilter[] fArray = new InputFilter[1];
                switch (checkedId) {
                    case R.id.isbn10_option:
                        barcodeFormat = BarcodeFormat.ISBN10;
                        ean.setInputType(InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
                        fArray[0] = new InputFilter.LengthFilter((int) getActivity().getResources().getInteger(R.integer.ean_size_isbn10));
                        ean.setFilters(fArray);
                        ean.setHint(getResources().getString(R.string.input_hint_isbn10));
                        break;
                    case R.id.isbn13_option:
                        barcodeFormat = BarcodeFormat.ISBN13;
                        ean.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_NORMAL);
                        fArray[0] = new InputFilter.LengthFilter((int) getActivity().getResources().getInteger(R.integer.ean_size_isbn13));
                        ean.setFilters(fArray);
                        ean.setHint(getResources().getString(R.string.input_hint_isbn13));
                        break;
                }
            }
        });

        rootView.findViewById(R.id.scan_button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(getActivity(), ScannerActivity.class);
                startActivityForResult(intent, BARCODE_SCAN_RESULT);

            }
        });

        rootView.findViewById(R.id.save_button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                //FIXME added snackbar showing isbn added when book info is cleared. This solves the third review case
                String sSnackbar  = getResources().getString(R.string.add_isbn);
                sSnackbar = sSnackbar.replace("_type_", barcodeFormat.getName());
                sSnackbar = sSnackbar.replace("_isbn_", eanInTextEditOrScannerActivity);
                Snackbar.make(view, sSnackbar, Snackbar.LENGTH_LONG)
                        .show();
                ean.setText("");
            }
        });

        rootView.findViewById(R.id.delete_button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent bookIntent = new Intent(getActivity(), BookService.class);
                bookIntent.putExtra(BookService.EAN, ean.getText().toString());
                bookIntent.setAction(BookService.DELETE_BOOK);
                getActivity().startService(bookIntent);
                ean.setText("");
            }
        });

        if(savedInstanceState!=null){
            ean.setText(savedInstanceState.getString(EAN_CONTENT));
            ean.setHint("");
        }

        return rootView;
    }

    private void searchBook(String ean){
        //Once we have an ISBN, start a book intent
        if(ean.length()==10 && barcodeFormat == BarcodeFormat.ISBN10){
            ean = getISBN13fromISBN10(ean);
        }
        Intent bookIntent = new Intent(getActivity(), BookService.class);
        bookIntent.putExtra(BookService.EAN, ean);
        bookIntent.setAction(BookService.FETCH_BOOK);
        getActivity().startService(bookIntent);
        AddBook.this.restartLoader();
    }

    private void restartLoader(){
        if(getLoaderManager() == null)
            return;
        getLoaderManager().restartLoader(LOADER_ID, null, this);
    }

    @Override
    public android.support.v4.content.Loader<Cursor> onCreateLoader(int id, Bundle args) {
        if(eanInTextEditOrScannerActivity.length()==0){
            return null;
        }
        String eanStr= eanInTextEditOrScannerActivity.toString();
        if(eanStr.length()==10 && barcodeFormat == BarcodeFormat.ISBN10){
            eanStr = getISBN13fromISBN10(eanStr);
        }
        return new CursorLoader(
                getActivity(),
                AlexandriaContract.BookEntry.buildFullBookUri(Long.parseLong(eanStr)),
                null,
                null,
                null,
                null
        );
    }

    @Override
    public void onLoadFinished(android.support.v4.content.Loader<Cursor> loader, Cursor data) {
        if (!data.moveToFirst()) {
            return;
        }

        String bookTitle = data.getString(data.getColumnIndex(AlexandriaContract.BookEntry.TITLE));
        //handle null cases
        if(bookTitle != null) {
            ((TextView) rootView.findViewById(R.id.bookTitle)).setText(bookTitle);
        }

        String bookSubTitle = data.getString(data.getColumnIndex(AlexandriaContract.BookEntry.SUBTITLE));
        //handle null cases
        if(bookSubTitle != null) {
            ((TextView) rootView.findViewById(R.id.bookSubTitle)).setText(bookSubTitle);
        }

        String authors = data.getString(data.getColumnIndex(AlexandriaContract.AuthorEntry.AUTHOR));
        //ERROR - if there is no author information, authors will be null, and will lead into a force close
        //if authors.split is called
        if(authors != null) {
            String[] authorsArr = authors.split(",");   //FIXME error here SOLVED
            ((TextView) rootView.findViewById(R.id.authors)).setLines(authorsArr.length);
            ((TextView) rootView.findViewById(R.id.authors)).setText(authors.replace(",", "\n"));
        }
        String imgUrl = data.getString(data.getColumnIndex(AlexandriaContract.BookEntry.IMAGE_URL));
        //handle null cases
        if(imgUrl != null && Patterns.WEB_URL.matcher(imgUrl).matches()){
            new DownloadImage((ImageView) rootView.findViewById(R.id.bookCover)).execute(imgUrl);
            rootView.findViewById(R.id.bookCover).setVisibility(View.VISIBLE);
        }

        String categories = data.getString(data.getColumnIndex(AlexandriaContract.CategoryEntry.CATEGORY));
        //handle null cases
        if(categories != null) {
            ((TextView) rootView.findViewById(R.id.categories)).setText(categories);
        }

        rootView.findViewById(R.id.save_button).setVisibility(View.VISIBLE);
        rootView.findViewById(R.id.delete_button).setVisibility(View.VISIBLE);
        //Hide keyboard
        InputMethodManager inputMethodManager = (InputMethodManager) getActivity().getSystemService(Activity.INPUT_METHOD_SERVICE);
        inputMethodManager.hideSoftInputFromWindow(getActivity().getCurrentFocus().getWindowToken(), 0);
    }

    @Override
    public void onLoaderReset(android.support.v4.content.Loader<Cursor> loader) {

    }

    private void clearFields(){
        ((TextView) rootView.findViewById(R.id.bookTitle)).setText("");
        ((TextView) rootView.findViewById(R.id.bookSubTitle)).setText("");
        ((TextView) rootView.findViewById(R.id.authors)).setText("");
        ((TextView) rootView.findViewById(R.id.categories)).setText("");
        rootView.findViewById(R.id.bookCover).setVisibility(View.INVISIBLE);
        rootView.findViewById(R.id.save_button).setVisibility(View.INVISIBLE);
        rootView.findViewById(R.id.delete_button).setVisibility(View.INVISIBLE);
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        activity.setTitle(R.string.scan);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if(requestCode == BARCODE_SCAN_RESULT){
            switch (resultCode){
                case Activity.RESULT_OK:{
                    if(data.hasExtra(ScannerActivity.EAN_FOUND) && data.hasExtra(ScannerActivity.EAN_FORMAT)){
                        if(data.getStringExtra(ScannerActivity.EAN_FORMAT).equals(BarcodeFormat.ISBN13.getName())
                                || data.getStringExtra(ScannerActivity.EAN_FORMAT).equals(BarcodeFormat.EAN13.getName())){
                            barcodeFormat = BarcodeFormat.ISBN13;
                        }else if(data.getStringExtra(ScannerActivity.EAN_FORMAT).equals(BarcodeFormat.ISBN10.getName())){
                            barcodeFormat = BarcodeFormat.ISBN10;
                        }
                        eanInTextEditOrScannerActivity = data.getStringExtra(ScannerActivity.EAN_FOUND);
                        searchBook(eanInTextEditOrScannerActivity);
                        break;
                    }
                    //the "break" is inside the if statement, so if data has no ean, it will handle the result canceled case
                }
                case Activity.RESULT_CANCELED:{
                    //book not found, the same procedure as in bookservice
                    Intent messageIntent = new Intent(MainActivity.MESSAGE_EVENT);
                    messageIntent.putExtra(MainActivity.MESSAGE_KEY,getResources().getString(R.string.operation_canceled));
                    LocalBroadcastManager.getInstance(getActivity()).sendBroadcast(messageIntent);
                    break;
                }
            }
        }
    }

    public static String getISBN13fromISBN10(String ean10){
        String ean13 = "";
        if(ean10.length() != 10)
            return ean13;
        //add 978 and remove check digit - last one
        ean13 = "978";
        ean13 = ean13.concat(ean10);
        StringBuilder sb = new StringBuilder(ean13);
        sb.deleteCharAt(sb.length()-1);
        ean13 = sb.toString();
        //calculate new check digit
        Integer digit = Character.getNumericValue(ean13.charAt(0)) + 3*Character.getNumericValue(ean13.charAt(1))
                + Character.getNumericValue(ean13.charAt(2)) + 3*Character.getNumericValue(ean13.charAt(3))
                + Character.getNumericValue(ean13.charAt(4)) + 3*Character.getNumericValue(ean13.charAt(5))
                + Character.getNumericValue(ean13.charAt(6)) + 3*Character.getNumericValue(ean13.charAt(7))
                + Character.getNumericValue(ean13.charAt(8)) + 3*Character.getNumericValue(ean13.charAt(9))
                + Character.getNumericValue(ean13.charAt(10)) + 3*Character.getNumericValue(ean13.charAt(11));
        digit = digit%10;
        if(digit != 0) digit = 10 - digit;
        return ean13.concat(digit.toString());
    }

}
