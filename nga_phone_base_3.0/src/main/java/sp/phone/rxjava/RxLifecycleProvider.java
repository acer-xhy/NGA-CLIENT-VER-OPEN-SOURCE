package sp.phone.rxjava;

import androidx.annotation.CheckResult;
import androidx.annotation.NonNull;

import com.trello.rxlifecycle3.LifecycleProvider;
import com.trello.rxlifecycle3.LifecycleTransformer;
import com.trello.rxlifecycle3.RxLifecycle;
import com.trello.rxlifecycle3.android.FragmentEvent;
import com.trello.rxlifecycle3.android.RxLifecycleAndroid;

import io.reactivex.Observable;
import io.reactivex.subjects.BehaviorSubject;

/**
 * Created by Justwen on 2017/11/27.
 */

public class RxLifecycleProvider implements LifecycleProvider<FragmentEvent> {

    private final BehaviorSubject<FragmentEvent> mLifecycleSubject = BehaviorSubject.create();

    @NonNull
    @CheckResult
    public final Observable<FragmentEvent> lifecycle() {
        return mLifecycleSubject.hide();
    }

    @NonNull
    @CheckResult
    public final <T> LifecycleTransformer<T> bindUntilEvent(@NonNull FragmentEvent event) {
        return RxLifecycle.bindUntilEvent(mLifecycleSubject, event);
    }

    @NonNull
    @CheckResult
    public final <T> LifecycleTransformer<T> bindToLifecycle() {
        return RxLifecycleAndroid.bindFragment(mLifecycleSubject);
    }

    public void onNext(FragmentEvent event) {
        mLifecycleSubject.onNext(event);
    }

}
