process.env.GOOGLE_CLIENT_ID = functions.config().google.client_id;
process.env.GOOGLE_CLIENT_SECRET = functions.config().google.client_secret;
process.env.REDIRECT_URI = functions.config().google.redirect_uri;
process.env.SERVICE_ACCOUNT_EMAIL = functions.config().google.service_account_email;
process.env.SERVICE_ACCOUNT_PRIVATE_KEY = functions.config().google.service_account_private_key;


const functions = require('firebase-functions');
const admin = require('firebase-admin');
const {google} = require('googleapis');

admin.initializeApp();

exports.getCalendarEvents = functions.https.onCall(async (data, context) => {
    // Verify Firebase ID token
    const idToken = data.idToken;
    const decodedToken = await admin.auth().verifyIdToken(idToken);
    const uid = decodedToken.uid;

    // Fetch user details from Firebase Authentication
    const user = await admin.auth().getUser(uid);

    // Ensure the user signed in via Google
    const googleProviderData = user.providerData.find(
        provider => provider.providerId === 'google.com'
    );

    if (!googleProviderData) {
        throw new functions.https.HttpsError(
            'permission-denied',
            'User is not authenticated with Google'
        );
    }

    const googleAccessToken = await admin.auth().createCustomToken(uid);

    const calendar = google.calendar({
        version: 'v3',
        auth: new google.auth.OAuth2(
            process.env.GOOGLE_CLIENT_ID,
            process.env.GOOGLE_CLIENT_SECRET
        )
    });

    calendar.context._options.auth.setCredentials({
        access_token: googleAccessToken
    });

    try {
        // Fetch calendar events
        const response = await calendar.events.list({
            calendarId: 'primary',
            timeMin: (new Date()).toISOString(),
            maxResults: 10,
            singleEvents: true,
            orderBy: 'startTime',
        });

        return {
            events: response.data.items
        };
    } catch (error) {
        throw new functions.https.HttpsError('internal', error.message);
    }
});