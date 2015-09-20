package com.example.tobiastrumm.freifunkautoconnect;

import android.app.Fragment;
import android.app.FragmentManager;
import android.content.Context;
import android.support.v13.app.FragmentPagerAdapter;
import android.view.ViewGroup;

public class MyFragmentPagerAdapter extends FragmentPagerAdapter {
    private final int PAGE_COUNT = 2;
    private String tabTitles[] = new String[] {"SSIDs", "Nearest AP"};
    private Context context;

    public AddRemoveNetworksFragment addRemoveNetworksFragment;
    public DummyFragment dummyFragment;

    public MyFragmentPagerAdapter(FragmentManager fm, Context context){
        super(fm);
        this.context = context;
    }
    @Override
    public Fragment getItem(int position) {
        switch(position){
            case 0:
                return AddRemoveNetworksFragment.newInstance();
            case 1:
                return DummyFragment.newInstance(2);
            default:
                return null;
        }
    }

    @Override
    public int getCount() {
        return PAGE_COUNT;
    }

    /**
     * Overwriting is necessary to get a reference to the Fragments created by the FragmentPageAdapter.
     * See http://stackoverflow.com/a/29269509/5354001 for more information.
     * @param container
     * @param position
     * @return
     */
    @Override
    public Object instantiateItem(ViewGroup container, int position) {
        Fragment createdFragment = (Fragment) super.instantiateItem(container, position);
        switch(position){
            case 0:
                addRemoveNetworksFragment = (AddRemoveNetworksFragment) createdFragment;
                break;
            case 1:
                dummyFragment = (DummyFragment) createdFragment;
                break;
        }
        return createdFragment;
    }

    @Override
    public CharSequence getPageTitle(int position) {
        return tabTitles[position];
    }
}
