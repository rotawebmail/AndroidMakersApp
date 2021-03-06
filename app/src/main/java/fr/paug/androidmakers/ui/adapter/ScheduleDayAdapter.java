package fr.paug.androidmakers.ui.adapter;

import android.content.Context;
import android.content.res.Resources;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.constraint.ConstraintLayout;
import android.support.text.emoji.EmojiCompat;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import fr.paug.androidmakers.R;
import fr.paug.androidmakers.manager.AgendaRepository;
import fr.paug.androidmakers.model.Session;
import fr.paug.androidmakers.model.Speaker;
import fr.paug.androidmakers.ui.util.SessionFilter;
import fr.paug.androidmakers.util.ScheduleSessionHelper;
import fr.paug.androidmakers.util.SessionSelector;
import fr.paug.androidmakers.util.TimeUtils;
import fr.paug.androidmakers.util.sticky_headers.StickyHeaders;

//TODO Filter
public class ScheduleDayAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder>
        implements StickyHeaders, StickyHeaders.ViewSetup {

    private Context context;
    private DaySchedule daySchedule;

    private static final long[] ID_ARRAY = new long[4];
    private static final int ITEM_TYPE_SESSION = 0;
    private static final int ITEM_TYPE_BREAK = 1;
    private static final int ITEM_TYPE_TIME_HEADER = 2;

    private final List<Object> mItems = new ArrayList<>();
    private final List<ScheduleSession> mSessions = new ArrayList<>();

    private final boolean mShowTimeSeparators;
    private final float stuckHeaderElevation;

    private final OnItemClickListener listener;
    private List<SessionFilter> sessionFilterList = new ArrayList<>();

    //region Constructor
    ScheduleDayAdapter(Context context, DaySchedule daySchedule, boolean showTimeSeparators, OnItemClickListener listener) {
        this.context = context;
        this.daySchedule = daySchedule;
        this.listener = listener;

        mShowTimeSeparators = showTimeSeparators;
        stuckHeaderElevation = context.getResources().getDimension(R.dimen.card_elevation);

        List<ScheduleSession> sessions = new ArrayList<>();
        for (RoomSchedule roomSchedule : daySchedule.getRoomSchedules()) {
            sessions.addAll(roomSchedule.getItems());
        }

        Collections.sort(sessions);
        setScheduleSessionList(sessions);
    }

    private void setScheduleSessionList(List<ScheduleSession> sessions) {
        this.mSessions.clear();
        this.mSessions.addAll(sessions);
        update();
    }

    public void setSessionFilterList(List<SessionFilter> sessionFilterList) {
        this.sessionFilterList.clear();
        this.sessionFilterList.addAll(sessionFilterList);
        update();
    }

    //endregion

    //region RecyclerView Override
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(final ViewGroup parent, final int viewType) {
        switch (viewType) {
            case ITEM_TYPE_SESSION:
                return new SessionItemViewHolder(LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.item_schedule_session, parent, false), listener, context);
//            case ITEM_TYPE_BREAK:
//                return NonSessionItemViewHolder.newInstance(parent);
            case ITEM_TYPE_TIME_HEADER:
                return new TimeSeparatorViewHolder(
                        LayoutInflater.from(parent.getContext())
                                .inflate(R.layout.schedule_item_time_separator, parent, false));
        }
        return null;
    }

    @Override
    public void onBindViewHolder(final RecyclerView.ViewHolder holder, final int position) {
        final Object item = mItems.get(position);
        switch (holder.getItemViewType()) {
            case ITEM_TYPE_SESSION:
                ((SessionItemViewHolder) holder).bind((ScheduleSession) item, daySchedule);
                break;
//            case ITEM_TYPE_BREAK:
//                ((NonSessionItemViewHolder) holder).bind((ScheduleItem) item);
//                break;
            case ITEM_TYPE_TIME_HEADER:
            default:
                ((TimeSeparatorViewHolder) holder).bind((TimeSeparatorItem) item);
                break;
        }
    }

    @Override
    public int getItemCount() {
        return mItems.size();
    }

    @Override
    public long getItemId(int position) {
        final Object item = mItems.get(position);
        if (item instanceof ScheduleSession) {
            return generateIdForScheduleItem((ScheduleSession) item);
        } else if (item instanceof TimeSeparatorItem) {
            return item.hashCode();
        }
        return position;
    }

    private static long generateIdForScheduleItem(@NonNull ScheduleSession item) {
        final long[] array = ID_ARRAY;
        // This code may look complex but its pretty simple. We need to use stable ids so that
        // any user interaction animations are run correctly (such as ripples). This means that
        // we need to generate a stable id. Not all items have sessionIds so we generate one
        // using the sessionId, title, start time and end time.
        array[0] = item.getSessionId();
        array[1] = !TextUtils.isEmpty(item.getTitle()) ? item.getTitle().hashCode() : 0;
        array[2] = item.getStartTimestamp();
        array[3] = item.getEndTimestamp();
        return Arrays.hashCode(array);
    }

    @Override
    public int getItemViewType(int position) {
        final Object item = mItems.get(position);
        if (item instanceof ScheduleSession) {
            //if (((ScheduleSession) item).type == ScheduleSession.BREAK) {
            //    return ITEM_TYPE_BREAK;
            //}
            return ITEM_TYPE_SESSION;
        } else if (item instanceof TimeSeparatorItem) {
            return ITEM_TYPE_TIME_HEADER;
        }
        return RecyclerView.INVALID_TYPE;
    }

    public int findTimeHeaderPositionForTime(final long time) {
        for (int pos = mItems.size() - 1; pos >= 0; pos--) {
            Object item = mItems.get(pos);
            // Keep going backwards until we find a time separator which has a start time before
            // now
            if (item instanceof TimeSeparatorItem && ((TimeSeparatorItem) item).startTime < time) {
                return pos;
            }
        }
        return 0;
    }
    //endregion

    //region Sticky headers Override
    @Override
    public boolean isStickyHeader(int position) {
        return getItemViewType(position) == ITEM_TYPE_TIME_HEADER;
    }

    @Override
    public void setupStickyHeaderView(View stickyHeader) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            stickyHeader.setTranslationZ(stuckHeaderElevation);
        }
    }

    @Override
    public void teardownStickyHeaderView(View stickyHeader) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            stickyHeader.setTranslationZ(0f);
        }
    }
    //endregion

    public void update() {
        mItems.clear();

        List<ScheduleSession> filteredSessions = new ArrayList<>();

        if (sessionFilterList.isEmpty()) {
            filteredSessions.addAll(mSessions);
        } else {
            for (ScheduleSession item : mSessions) {
                for (SessionFilter sessionFilter : sessionFilterList) {
                    boolean matched = false;

                    switch (sessionFilter.type) {
                        case BOOKMARK: {
                            matched = SessionSelector.getInstance().isSelected(item.getSessionId());
                            break;
                        }
                        case LANGUAGE: {
                            matched = sessionFilter.value.equals(item.getLanguage());
                            break;
                        }
                        case ROOM: {
                            matched = sessionFilter.value.equals(item.getRoomId());
                            break;
                        }
                    }

                    if (matched) {
                        filteredSessions.add(item);
                    }
                }
            }
        }

        if (!mShowTimeSeparators) {
            mItems.addAll(filteredSessions);
        } else {
            for (int i = 0, size = filteredSessions.size(); i < size; i++) {
                final ScheduleSession prev = i > 0 ? filteredSessions.get(i - 1) : null;
                final ScheduleSession item = filteredSessions.get(i);

                if (prev == null || !ScheduleSessionHelper.sameStartTime(prev, item, true)) {
                    mItems.add(new TimeSeparatorItem(item));
                }
                mItems.add(item);
            }
        }

        // TODO use DiffUtil
        notifyDataSetChanged();
    }

    //region Time Separator
    private static class TimeSeparatorViewHolder extends RecyclerView.ViewHolder {
        private final TextView mStartTime;

        TimeSeparatorViewHolder(final View itemView) {
            super(itemView);
            mStartTime = (TextView) itemView;
        }

        void bind(@NonNull final TimeSeparatorItem item) {
            mStartTime.setText(TimeUtils.formatShortTime(itemView.getContext(), new Date(item.startTime)));
        }
    }

    private static class TimeSeparatorItem {
        private final long startTime;

        TimeSeparatorItem(ScheduleSession item) {
            this.startTime = item.getStartTimestamp();
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            final TimeSeparatorItem that = (TimeSeparatorItem) o;
            return startTime == that.startTime;
        }

        @Override
        public int hashCode() {
            return (int) (startTime ^ (startTime >>> 32));
        }
    }
    //endregion

    //region Session
    public static class SessionItemViewHolder extends RecyclerView.ViewHolder {
        ConstraintLayout sessionLayout;
        TextView sessionTitle;
        TextView sessionDescription;
        ImageButton sessionBookmark;

        private final OnItemClickListener listener;
        private Context context;

        SessionItemViewHolder(View itemView, OnItemClickListener onItemClickListener, Context ctx) {
            super(itemView);
            sessionLayout = itemView.findViewById(R.id.sessionItemLayout);
            sessionTitle = itemView.findViewById(R.id.sessionTitleTextView);
            sessionDescription = itemView.findViewById(R.id.sessionDescriptionTextView);
            sessionBookmark = itemView.findViewById(R.id.bookmark);
            listener = onItemClickListener;
            context = ctx;
        }

        void bind(@NonNull final ScheduleSession scheduleSession, DaySchedule daySchedule) {
            // Session title
            sessionTitle.setText(scheduleSession.getTitle());

            final String sessionDuration = TimeUtils.formatDuration(itemView.getContext(),
                    scheduleSession.getStartTimestamp(), scheduleSession.getEndTimestamp());
            final String roomTitle = getRoomTitle(scheduleSession, daySchedule);

            StringBuilder descriptionBuilder = new StringBuilder();
            final Resources resources = itemView.getResources();

            if (EmojiCompat.get().getLoadState() == EmojiCompat.LOAD_STATE_SUCCEEDED
                    && Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                if (roomTitle.isEmpty()) {
                    descriptionBuilder.append(resources.getString(R.string.session_description_placeholder,
                            sessionDuration, EmojiCompat.get().process(scheduleSession.getLanguageInEmoji())));
                } else {
                    // We need to check the status of EmojiCompat if we want to avoid a crash at 1st launch
                    descriptionBuilder.append(resources.getString(R.string.session_description_placeholder_with_language,
                            sessionDuration, roomTitle, EmojiCompat.get().process(scheduleSession.getLanguageInEmoji())));
                }
            } else {
                final int languageStringRes = Session.getLanguageFullName(scheduleSession.getLanguage());
                if (roomTitle.isEmpty()) {
                    if (languageStringRes != 0) {
                        descriptionBuilder.append(resources.getString(R.string.session_description_placeholder,
                                sessionDuration, resources.getString(languageStringRes)));
                    } else {
                        descriptionBuilder.append(sessionDuration);
                    }
                } else {
                    if (languageStringRes != 0) {
                        descriptionBuilder.append(resources.getString(R.string.session_description_placeholder_with_language,
                                sessionDuration, roomTitle, resources.getString(languageStringRes)));
                    } else {
                        descriptionBuilder.append(resources.getString(R.string.session_description_placeholder,
                                sessionDuration, roomTitle));
                    }
                }
            }

            Session session = AgendaRepository.getInstance().getSession(scheduleSession.getSessionId());
            if (session != null && session.speakers != null) {
                descriptionBuilder.append('\n');
                for (int i = 0; i < session.speakers.length; i++) {
                    final Speaker speaker = AgendaRepository.getInstance().getSpeaker(session.speakers[i]);

                    if (speaker != null) {
                        descriptionBuilder.append(speaker.getFullName().trim());
                        if (i != session.speakers.length - 1) descriptionBuilder.append(", ");
                    }
                }
            }

            sessionDescription.setText(descriptionBuilder.toString());

            sessionLayout.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    listener.onItemClick(scheduleSession);
                }
            });

            sessionBookmark.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(final View view) {
                    sessionBookmark.setActivated(!sessionBookmark.isActivated());
                    SessionSelector.getInstance().setSessionSelected(scheduleSession.getSessionId(), sessionBookmark.isActivated());
                    if (sessionBookmark.isActivated()) {
                        ScheduleSessionHelper.scheduleStarredSession(context,
                                scheduleSession.getStartTimestamp(),
                                scheduleSession.getEndTimestamp(),
                                scheduleSession.getSessionId());
                    } else {
                        ScheduleSessionHelper.unScheduleSession(context, scheduleSession.getSessionId());
                    }
                }
            });
            sessionBookmark.setActivated(SessionSelector.getInstance().isSelected(scheduleSession.getSessionId()));
        }

        private String getRoomTitle(@NonNull ScheduleSession scheduleSession, DaySchedule daySchedule) {
            String roomTitle = "";
            for (RoomSchedule roomSchedule : daySchedule.getRoomSchedules()) {
                if (roomSchedule.getRoomId() == scheduleSession.getRoomId()) {
                    roomTitle = roomSchedule.getTitle();
                }
            }
            return roomTitle;
        }
    }
    //endregion

    public interface OnItemClickListener {
        void onItemClick(ScheduleSession scheduleSession);
    }

}